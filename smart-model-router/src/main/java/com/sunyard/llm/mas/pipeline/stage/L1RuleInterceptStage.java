package com.sunyard.llm.mas.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.service.ApiKeyService;
import com.sunyard.llm.mas.service.AppProfileService;
import com.sunyard.llm.mas.service.BlacklistService;
import com.sunyard.llm.mas.service.PolicyRuntimeService;
import com.sunyard.llm.mas.service.RateLimitService;
import com.sunyard.llm.mas.service.RuleRateLimitService;
import com.sunyard.llm.mas.service.SecurityEventService;
import com.sunyard.llm.mas.service.SensitiveWordFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * L1 规则拦截层（§5.1 / 附录 G.7）：
 * Bearer 校验（G.1，mas.auth.enabled=false 时跳过）→ 黑名单 → 敏感词 AC 匹配 → 频率限制 → 参数校验。
 * 规则组件自身异常时 fail-open 放行。
 * §8 已知限制消除：鉴权从“仅校验非空”升级为 API Key 验证体系。
 * 安全拦截（黑名单/敏感词/限流/策略/鉴权失败）实时写 mas_security_event（fire-and-forget）。
 */
@Component
public class L1RuleInterceptStage {

    private static final Logger log = LoggerFactory.getLogger(L1RuleInterceptStage.class);
    private static final int MAX_OUTPUT_TOKENS = 8192;
    /** 超限行为 = 排队等待时，允许的最长等待（毫秒） */
    private static final int QUEUE_WAIT_MS = 500;

    private final BlacklistService blacklistService;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final RateLimitService rateLimitService;
    private final ApiKeyService apiKeyService;
    private final RuleRateLimitService ruleRateLimitService;
    private final PolicyRuntimeService policyRuntime;
    private final AppProfileService appProfile;
    private final SecurityEventService securityEventService;
    private final MasProperties props;

    public L1RuleInterceptStage(BlacklistService blacklistService,
                                SensitiveWordFilter sensitiveWordFilter,
                                RateLimitService rateLimitService,
                                ApiKeyService apiKeyService,
                                RuleRateLimitService ruleRateLimitService,
                                PolicyRuntimeService policyRuntime,
                                AppProfileService appProfile,
                                SecurityEventService securityEventService,
                                MasProperties props) {
        this.blacklistService = blacklistService;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.rateLimitService = rateLimitService;
        this.apiKeyService = apiKeyService;
        this.ruleRateLimitService = ruleRateLimitService;
        this.policyRuntime = policyRuntime;
        this.appProfile = appProfile;
        this.securityEventService = securityEventService;
        this.props = props;
    }

    /** 拦截实时落安全事件（异步，不阻塞、不影响拦截本身） */
    private void securityEvent(PipelineContext ctx, String eventType, String severity,
                               String reasonCode, String reasonText) {
        if (securityEventService != null) {
            securityEventService.recordPipelineEvent(ctx.getTraceId(), ctx.getAppId(), ctx.getUserId(),
                    ctx.getRequestedModel(), "L1", eventType, severity, reasonCode, reasonText);
        }
    }

    public Mono<Void> check(PipelineContext ctx) {
        // 鉴权先行（异步），通过后执行其余同步检查
        return checkAuth(ctx)
                .then(Mono.fromCallable(() -> {
                    // SLA 优先级保障（POC 第 7 问）：P0 关键业务在资源紧张时不被降级、不排队
                    String sla = appProfile == null ? "P1" : appProfile.slaOf(ctx.getAppId());
                    ctx.setSlaLevel(sla);
                    ctx.getMeta().setSlaLevel(sla);
                    boolean critical = "P0".equalsIgnoreCase(sla);
                    if (blacklistService.isBlacklisted(ctx.getUserId())) {
                        securityEvent(ctx, "BLACKLIST", "HIGH", "blacklisted",
                                "用户在黑名单中：" + ctx.getUserId());
                        throw MasException.blacklisted(ctx.getUserId());
                    }
                    try {
                        checkSensitiveWords(ctx.getRequest());
                    } catch (MasException e) {
                        securityEvent(ctx, "SENSITIVE_LEAK", "HIGH", "input_blocked",
                                "请求命中敏感词拦截（AC 自动机匹配）");
                        throw e;
                    }
                    if (!rateLimitService.tryAcquire(ctx.getUserId())) {
                        securityEvent(ctx, "RATE_ABUSE", "MEDIUM", "rate_limited",
                                "用户级频率限制触发：" + ctx.getUserId());
                        throw MasException.rateLimited(ctx.getUserId());
                    }
                    // 管理面配置的限流规则（按部门/应用/Key/模型维度）：此前从不生效
                    RuleRateLimitService.Decision decision = RuleRateLimitService.Decision.pass();
                    if (ruleRateLimitService != null) {
                        decision = ruleRateLimitService.check(ctx);
                        if (decision.rejected()) {
                            securityEvent(ctx, "RATE_ABUSE", "MEDIUM", "rate_limited",
                                    "规则限流触发：" + decision.reason());
                            throw MasException.rateLimited(ctx.getUserId() + " / " + decision.reason());
                        }
                        if (decision.action() == RuleRateLimitService.Action.DOWNGRADE && !critical) {
                            // P0 关键业务保留原模型档位，不让"用能力换可用性"落在关键业务上
                            ctx.setDowngraded(true);
                        }
                    }
                    // 统一控制面：安全类策略在拦截链末端生效，命中即拒绝并留痕
                    if (policyRuntime != null) {
                        String blocked = policyRuntime.evaluateL1(ctx);
                        if (blocked != null) {
                            securityEvent(ctx, "POLICY_BLOCK", "HIGH", "policy_blocked", blocked);
                            throw MasException.invalidParam(blocked);
                        }
                    }
                    checkParams(ctx.getRequest());
                    return critical && decision.action() == RuleRateLimitService.Action.QUEUE
                            ? RuleRateLimitService.Decision.pass()   // P0 免排队，直接放行
                            : decision;
                }))
                // QUEUE 动作：短暂排队后放行，而不是直接拒绝（超限行为可配置）
                .flatMap(decision -> decision.action() == RuleRateLimitService.Action.QUEUE
                        && ruleRateLimitService != null
                        ? ruleRateLimitService.queueDelay(queueWaitMs(ctx)).then()
                        : Mono.<Void>empty());
    }

    /** 排队等待时长按 SLA 分档：P0 免排队、P1 半档、P2/P3 全档 */
    private static int queueWaitMs(PipelineContext ctx) {
        String sla = ctx.getSlaLevel();
        if ("P0".equalsIgnoreCase(sla)) return 0;
        if ("P1".equalsIgnoreCase(sla)) return QUEUE_WAIT_MS / 2;
        return QUEUE_WAIT_MS;
    }

    /** §8 改进：API Key 验证体系（替代原来的“仅校验非空”） */
    private Mono<Void> checkAuth(PipelineContext ctx) {
        if (!props.getAuth().isEnabled()) {
            // 鉴权关闭（内网零改造接入）：身份链 body.user → X-User-Id → anonymous
            if (ctx.getAgentId() != null && !ctx.getAgentId().isBlank()) {
                ctx.setUserId(ctx.getAgentId());
            }
            return Mono.empty();
        }
        String auth = ctx.getAuthorization();
        if (auth == null || auth.isBlank() || !auth.startsWith("Bearer ") || auth.substring(7).trim().isEmpty()) {
            securityEvent(ctx, "AUTH_FAILURE", "MEDIUM", "invalid_api_key",
                    "缺少或格式非法的 Authorization Bearer 凭证");
            return Mono.error(MasException.unauthorized());
        }
        String token = auth.substring(7).trim();
        return apiKeyService.validate(token)
                // fail-closed：validate 未发出任何信号（空完成）一律拒绝，杜绝静默放行
                .switchIfEmpty(Mono.error(new IllegalStateException("api key validation returned empty")))
                .doOnNext(info -> {
                    // 验证通过：将 userId 写入上下文
                    ctx.setUserId(info.userId());
                    if (info.appId() != null) {
                        ctx.setAppId(info.appId());
                    }
                })
                .onErrorResume(e -> {
                    log.debug("API Key validation failed: {}", e.getMessage());
                    securityEvent(ctx, "AUTH_FAILURE", "MEDIUM", "invalid_api_key",
                            "API Key 校验失败：" + e.getMessage());
                    return Mono.error(MasException.unauthorized());
                })
                .then();
    }

    private void checkSensitiveWords(JsonNode request) {
        try {
            JsonNode messages = request.path("messages");
            if (!messages.isArray()) {
                return;
            }
            for (JsonNode m : messages) {
                if ("user".equals(m.path("role").asText())
                        && sensitiveWordFilter.contains(m.path("content").asText(""))) {
                    throw MasException.inputBlocked("");
                }
            }
        } catch (MasException e) {
            throw e;
        } catch (Exception e) {
            // 敏感词组件异常时的降级方向由 mas.governance.fail-closed 决定：
            // 默认 fail-open 放行；fail-closed=true 时按内容拦截拒绝（安全优先）
            boolean failClosed = props.getGovernance() != null && props.getGovernance().isFailClosed();
            log.warn("Sensitive word check degraded ({}): {}", failClosed ? "fail-closed" : "fail-open", e.getMessage());
            if (failClosed) {
                throw MasException.inputBlocked("");
            }
        }
    }

    private void checkParams(JsonNode request) {
        JsonNode messages = request.path("messages");
        if (!messages.isArray() || messages.isEmpty()) {
            throw MasException.invalidParam("messages must be a non-empty array");
        }
        JsonNode maxTokens = request.path("max_tokens");
        if (maxTokens.isInt() && maxTokens.asInt() > MAX_OUTPUT_TOKENS) {
            throw MasException.invalidParam("max_tokens exceeds limit " + MAX_OUTPUT_TOKENS);
        }
        if (maxTokens.isInt() && maxTokens.asInt() <= 0) {
            throw MasException.invalidParam("max_tokens must be positive");
        }
    }
}

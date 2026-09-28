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
    private final MasProperties props;

    public L1RuleInterceptStage(BlacklistService blacklistService,
                                SensitiveWordFilter sensitiveWordFilter,
                                RateLimitService rateLimitService,
                                ApiKeyService apiKeyService,
                                RuleRateLimitService ruleRateLimitService,
                                PolicyRuntimeService policyRuntime,
                                AppProfileService appProfile,
                                MasProperties props) {
        this.blacklistService = blacklistService;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.rateLimitService = rateLimitService;
        this.apiKeyService = apiKeyService;
        this.ruleRateLimitService = ruleRateLimitService;
        this.policyRuntime = policyRuntime;
        this.appProfile = appProfile;
        this.props = props;
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
                        throw MasException.blacklisted(ctx.getUserId());
                    }
                    checkSensitiveWords(ctx.getRequest());
                    if (!rateLimitService.tryAcquire(ctx.getUserId())) {
                        throw MasException.rateLimited(ctx.getUserId());
                    }
                    // 管理面配置的限流规则（按部门/应用/Key/模型维度）：此前从不生效
                    RuleRateLimitService.Decision decision = RuleRateLimitService.Decision.pass();
                    if (ruleRateLimitService != null) {
                        decision = ruleRateLimitService.check(ctx);
                        if (decision.rejected()) {
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
        if (auth == null || auth.isBlank()) {
            return Mono.error(MasException.unauthorized());
        }
        if (!auth.startsWith("Bearer ")) {
            return Mono.error(MasException.unauthorized());
        }
        String token = auth.substring(7).trim();
        if (token.isEmpty()) {
            return Mono.error(MasException.unauthorized());
        }
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
            log.warn("Sensitive word check degraded (fail-open): {}", e.getMessage());
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

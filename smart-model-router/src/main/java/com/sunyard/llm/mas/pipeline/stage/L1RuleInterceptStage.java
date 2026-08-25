package com.sunyard.llm.mas.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.service.BlacklistService;
import com.sunyard.llm.mas.service.RateLimitService;
import com.sunyard.llm.mas.service.SensitiveWordFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * L1 规则拦截层（§5.1 / 附录 G.7）：
 * Bearer 校验（G.1）→ 黑名单 → 敏感词 AC 匹配 → 频率限制 → 参数校验。
 * 规则组件自身异常时 fail-open 放行。
 */
@Component
public class L1RuleInterceptStage {

    private static final Logger log = LoggerFactory.getLogger(L1RuleInterceptStage.class);
    private static final int MAX_OUTPUT_TOKENS = 8192;

    private final BlacklistService blacklistService;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final RateLimitService rateLimitService;

    public L1RuleInterceptStage(BlacklistService blacklistService,
                                SensitiveWordFilter sensitiveWordFilter,
                                RateLimitService rateLimitService) {
        this.blacklistService = blacklistService;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.rateLimitService = rateLimitService;
    }

    public Mono<Void> check(PipelineContext ctx) {
        return Mono.fromRunnable(() -> {
            checkAuth(ctx);
            if (blacklistService.isBlacklisted(ctx.getUserId())) {
                throw MasException.blacklisted(ctx.getUserId());
            }
            checkSensitiveWords(ctx.getRequest());
            if (!rateLimitService.tryAcquire(ctx.getUserId())) {
                throw MasException.rateLimited(ctx.getUserId());
            }
            checkParams(ctx.getRequest());
        }).then();
    }

    /** 附录 G.1：Authorization 若提供则必须非空 Bearer */
    private void checkAuth(PipelineContext ctx) {
        String auth = ctx.getAuthorization();
        if (auth == null) {
            return;
        }
        String token = auth.startsWith("Bearer ") ? auth.substring(7).trim() : auth.trim();
        if (token.isEmpty()) {
            throw MasException.unauthorized();
        }
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

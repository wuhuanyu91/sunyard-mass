package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.mapper.QuotaMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * L4 Token 配额：对 mas_token_quota 原子累加，超限返回 402（附录 G.7）。
 * 行不存在或已过期时自动按配置限额重置。
 */
@Service
public class QuotaService {

    private static final Logger log = LoggerFactory.getLogger(QuotaService.class);

    private final MasProperties props;
    private final QuotaMapper mapper;

    public QuotaService(MasProperties props, QuotaMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /** 预扣 token 额度（分钟 + 天两个维度），任一超限即 402 */
    public Mono<Void> reserve(String userId, long tokens) {
        if (tokens <= 0) {
            return Mono.empty();
        }
        Instant now = Instant.now();
        Instant minuteReset = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
        Instant dayReset = LocalDateTime.now(ZoneId.systemDefault())
                .toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return checkAndAdd(userId, "minute", props.getQuota().getPerUserPerMinute(), tokens, minuteReset)
                .then(checkAndAdd(userId, "day", props.getQuota().getPerUserPerDay(), tokens, dayReset))
                .onErrorResume(e -> !(e instanceof MasException), e -> {
                    // 配额组件异常时的降级方向由 mas.governance.fail-closed 决定：
                    // 默认 fail-open 放行（可用性优先）；fail-closed=true 时按 402 拒绝（安全优先）
                    boolean failClosed = props.getGovernance() != null && props.getGovernance().isFailClosed();
                    log.warn("Quota check degraded ({}): {}", failClosed ? "fail-closed" : "fail-open", e.getMessage());
                    return failClosed ? Mono.error(MasException.quotaExceeded()) : Mono.empty();
                });
    }

    /** 结算：退还预扣与实际使用之差 */
    public Mono<Void> settle(String userId, long reservedTokens, long actualTokens) {
        long refund = reservedTokens - actualTokens;
        if (refund <= 0) {
            return Mono.empty();
        }
        Instant now = Instant.now();
        Instant minuteReset = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
        Instant dayReset = LocalDateTime.now(ZoneId.systemDefault())
                .toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        return refundQuota(userId, "minute", refund, minuteReset)
                .then(refundQuota(userId, "day", refund, dayReset))
                .onErrorResume(e -> {
                    log.warn("Quota settle degraded (skip refund): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> refundQuota(String userId, String period, long tokens, Instant resetAt) {
        LocalDateTime resetDateTime = LocalDateTime.ofInstant(resetAt, ZoneId.systemDefault());
        return ReactiveDbAdapter.mono(() -> mapper.refundQuota(userId, period, tokens)).then();
    }

    private Mono<Void> checkAndAdd(String userId, String period, long limit, long tokens, Instant resetAt) {
        LocalDateTime resetDateTime = LocalDateTime.ofInstant(resetAt, ZoneId.systemDefault());
        return ReactiveDbAdapter.mono(() -> mapper.upsertQuota(userId, period, limit, resetDateTime))
                .then(Mono.defer(() -> ReactiveDbAdapter.mono(() -> mapper.updateQuotaUsed(userId, period, tokens))
                        .flatMap(updated -> updated > 0
                                ? Mono.<Void>empty()
                                : Mono.error(MasException.quotaExceeded()))));
    }
}

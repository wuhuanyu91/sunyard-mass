package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.exception.MasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
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
    private final DatabaseClient db;

    public QuotaService(MasProperties props, DatabaseClient db) {
        this.props = props;
        this.db = db;
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
                    // DB 异常时 fail-open 放行（与 L1 降级策略一致）
                    log.warn("Quota check degraded (fail-open): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> checkAndAdd(String userId, String period, long limit, long tokens, Instant resetAt) {
        String upsert = """
                INSERT INTO mas_token_quota (quota_type, quota_key, period, token_limit, token_used, reset_at)
                VALUES ('user', :k, :p, :limit, 0, :reset)
                ON CONFLICT (quota_type, quota_key, period) DO UPDATE
                SET reset_at = EXCLUDED.reset_at, token_used = 0, token_limit = EXCLUDED.token_limit
                WHERE mas_token_quota.reset_at <= now()
                """;
        return db.sql(upsert)
                .bind("k", userId)
                .bind("p", period)
                .bind("limit", limit)
                .bind("reset", resetAt)
                .then()
                .then(Mono.defer(() -> db.sql("""
                                UPDATE mas_token_quota
                                SET token_used = token_used + :t
                                WHERE quota_type = 'user' AND quota_key = :k AND period = :p
                                  AND reset_at > now() AND token_used + :t <= token_limit
                                """)
                        .bind("t", tokens)
                        .bind("k", userId)
                        .bind("p", period)
                        .fetch().rowsUpdated()
                        .flatMap(updated -> updated > 0
                                ? Mono.<Void>empty()
                                : Mono.error(MasException.quotaExceeded()))));
    }
}

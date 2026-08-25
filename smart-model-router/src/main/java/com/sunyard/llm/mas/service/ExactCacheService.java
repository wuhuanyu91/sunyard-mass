package com.sunyard.llm.mas.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunyard.llm.mas.config.MasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * L2.1 精确缓存：本地 Caffeine（一级）+ PostgreSQL mas_exact_cache（二级）。
 * 命中回填本地；写入异步、失败仅告警不阻断（附录 G.4/G.7）。
 */
@Service
public class ExactCacheService {

    private static final Logger log = LoggerFactory.getLogger(ExactCacheService.class);

    private final DatabaseClient db;
    private final MasProperties props;
    private final Cache<String, String> local;

    public ExactCacheService(DatabaseClient db, MasProperties props) {
        this.db = db;
        this.props = props;
        this.local = Caffeine.newBuilder()
                .maximumSize(props.getCache().getMaxExactEntries())
                .expireAfterWrite(props.getCache().getExactTtl())
                .build();
    }

    public Mono<String> get(String key) {
        String hit = local.getIfPresent(key);
        if (hit != null) {
            return Mono.just(hit);
        }
        return db.sql("SELECT response_json FROM mas_exact_cache WHERE cache_key = :k AND expire_at > now()")
                .bind("k", key)
                .map(row -> row.get("response_json", String.class))
                .one()
                .doOnNext(v -> local.put(key, v))
                .onErrorResume(e -> {
                    log.warn("Exact cache read degraded (skip): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> put(String key, String modelId, String responseJson) {
        local.put(key, responseJson);
        return db.sql("""
                        INSERT INTO mas_exact_cache (cache_key, model_id, response_json, expire_at)
                        VALUES (:k, :m, :r, now() + CAST(:ttl || ' seconds' AS INTERVAL))
                        ON CONFLICT (cache_key) DO UPDATE
                        SET response_json = EXCLUDED.response_json, expire_at = EXCLUDED.expire_at
                        """)
                .bind("k", key)
                .bind("m", modelId == null ? "" : modelId)
                .bind("r", responseJson)
                .bind("ttl", String.valueOf(props.getCache().getExactTtl().toSeconds()))
                .then()
                .onErrorResume(e -> {
                    log.warn("Exact cache write degraded (skip): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** 清空本地与 DB 两级（测试/管理端点使用） */
    public Mono<Void> flush() {
        local.invalidateAll();
        return db.sql("DELETE FROM mas_exact_cache")
                .fetch().rowsUpdated()
                .then()
                .onErrorResume(e -> {
                    log.warn("Exact cache flush degraded (skip): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** 定时清理过期行 */
    @Scheduled(fixedDelay = 600_000)
    public void purgeExpired() {
        db.sql("DELETE FROM mas_exact_cache WHERE expire_at < now()")
                .fetch().rowsUpdated()
                .subscribe(n -> log.debug("Exact cache purged {} rows", n),
                        e -> log.warn("Exact cache purge failed: {}", e.getMessage()));
    }
}

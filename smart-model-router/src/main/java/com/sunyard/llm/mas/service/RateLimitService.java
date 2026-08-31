package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.mapper.RateLimitMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * L1 频率限制（§8 已知限制消除 — 分布式限流）：
 * 替代 Bucket4j 纯内存桶，迁移至 PostgreSQL 原子操作，支持多实例部署。
 * 按秒级滑动窗口计数，每用户每秒最多 perUserQps 次请求。
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final DateTimeFormatter WINDOW_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MasProperties props;
    private final RateLimitMapper mapper;

    public RateLimitService(MasProperties props, RateLimitMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    /** @return true 放行，false 超限 */
    public boolean tryAcquire(String userId) {
        int qps = Math.max(1, props.getRateLimit().getPerUserQps());
        LocalDateTime now = LocalDateTime.now();
        String windowKey = now.format(WINDOW_FMT);
        LocalDateTime windowEnd = now.plusSeconds(1);
        try {
            int affected = mapper.tryAcquire(userId, windowKey, qps, windowEnd);
            return affected > 0;
        } catch (Exception e) {
            // DB 异常时 fail-open 放行（与 L1 降级策略一致）
            log.warn("Rate limit check degraded (fail-open): {}", e.getMessage());
            return true;
        }
    }

    /** 定时清理过期窗口行（60 秒一次） */
    @Scheduled(fixedDelay = 60_000)
    public void purgeExpired() {
        ReactiveDbAdapter.mono(mapper::purgeExpired)
                .subscribe(
                        n -> { if (n > 0) log.debug("Rate limit purged {} expired rows", n); },
                        e -> log.warn("Rate limit purge failed: {}", e.getMessage()));
    }
}

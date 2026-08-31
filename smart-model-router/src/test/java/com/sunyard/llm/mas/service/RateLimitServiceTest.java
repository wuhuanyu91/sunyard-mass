package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.mapper.RateLimitMapper;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分布式限流测试（§8 已知限制消除 — 替代 Bucket4j）：
 * 使用模拟 Mapper 验证限流逻辑。
 */
class RateLimitServiceTest {

    /** 模拟 Mapper：内存计数实现，行为与 PG 原子操作一致 */
    private static RateLimitMapper mockMapper() {
        ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();
        return new RateLimitMapper() {
            @Override
            public int tryAcquire(String userId, String windowKey, int limit, java.time.LocalDateTime windowEnd) {
                String key = userId + ":" + windowKey;
                AtomicInteger count = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
                int current = count.incrementAndGet();
                return current <= limit ? 1 : 0;
            }
            @Override
            public int purgeExpired() { return 0; }
        };
    }

    private RateLimitService newService(int qps) {
        MasProperties props = new MasProperties();
        props.getRateLimit().setPerUserQps(qps);
        return new RateLimitService(props, mockMapper());
    }

    @Test
    void allowsUpToQpsThenRejects() {
        RateLimitService limiter = newService(3);
        assertTrue(limiter.tryAcquire("user-a"));
        assertTrue(limiter.tryAcquire("user-a"));
        assertTrue(limiter.tryAcquire("user-a"));
        assertFalse(limiter.tryAcquire("user-a"));
    }

    @Test
    void bucketsArePerUser() {
        RateLimitService limiter = newService(1);
        assertTrue(limiter.tryAcquire("user-a"));
        assertFalse(limiter.tryAcquire("user-a"));
        // 另一用户独立分桶，不受影响
        assertTrue(limiter.tryAcquire("user-b"));
    }
}

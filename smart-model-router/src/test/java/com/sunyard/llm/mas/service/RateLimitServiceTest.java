package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bucket4j 限流窗口（附录 G.7）：按用户分桶、每秒 QPS 上限。
 */
class RateLimitServiceTest {

    private RateLimitService newService(int qps) {
        MasProperties props = new MasProperties();
        props.getRateLimit().setPerUserQps(qps);
        return new RateLimitService(props);
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

    @Test
    void windowRefillsAfterOneSecond() throws InterruptedException {
        RateLimitService limiter = newService(1);
        assertTrue(limiter.tryAcquire("user-a"));
        assertFalse(limiter.tryAcquire("user-a"));
        Thread.sleep(1100);
        assertTrue(limiter.tryAcquire("user-a"));
    }
}

package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bucket4j 限流边界测试：零/负 QPS 下限兜底、高吞吐不误杀、多用户桶隔离。
 */
class RateLimitServiceBoundaryTest {

    private RateLimitService newService(int qps) {
        MasProperties props = new MasProperties();
        props.getRateLimit().setPerUserQps(qps);
        return new RateLimitService(props);
    }

    @Test
    void zeroQpsFlooredToOnePerSecond() {
        RateLimitService limiter = newService(0);
        assertTrue(limiter.tryAcquire("u"), "qps=0 应兜底为 1，首次请求放行");
        assertFalse(limiter.tryAcquire("u"), "第二次应被拒绝");
    }

    @Test
    void negativeQpsFlooredToOnePerSecond() {
        RateLimitService limiter = newService(-5);
        assertTrue(limiter.tryAcquire("u"));
        assertFalse(limiter.tryAcquire("u"));
    }

    @Test
    void highQpsDoesNotFalseRejectWithinBudget() {
        RateLimitService limiter = newService(1000);
        for (int i = 0; i < 200; i++) {
            assertTrue(limiter.tryAcquire("burst-user"), "第 " + i + " 次不应被拒绝");
        }
    }

    @Test
    void manyUsersKeepIndependentBuckets() {
        RateLimitService limiter = newService(1);
        for (int i = 0; i < 50; i++) {
            assertTrue(limiter.tryAcquire("user-" + i), "新用户首个请求应放行");
        }
        // 所有用户配额耗尽后均被拒绝
        for (int i = 0; i < 50; i++) {
            assertFalse(limiter.tryAcquire("user-" + i));
        }
    }

    @Test
    void anonymousUserIdIsAValidBucketKey() {
        RateLimitService limiter = newService(1);
        assertTrue(limiter.tryAcquire("anonymous"));
        assertFalse(limiter.tryAcquire("anonymous"));
    }
}

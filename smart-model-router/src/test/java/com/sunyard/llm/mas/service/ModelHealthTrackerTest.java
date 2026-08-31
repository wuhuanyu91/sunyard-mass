package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 熔断器状态机测试（CLOSED → OPEN → HALF_OPEN → CLOSED）。
 */
class ModelHealthTrackerTest {

    private ModelHealthTracker tracker;
    private MasProperties props;

    @BeforeEach
    void setUp() {
        props = new MasProperties();
        // 测试用：降低阈值加速状态转换
        props.getCircuitBreaker().setFailureThreshold(3);
        props.getCircuitBreaker().setOpenDuration(Duration.ofMillis(200));
        props.getCircuitBreaker().setHalfOpenMaxAttempts(2);
        tracker = new ModelHealthTracker(props);
    }

    @Test
    void initialStateIsClosed() {
        assertTrue(tracker.isAvailable("http://endpoint-a"));
    }

    @Test
    void consecutiveFailuresOpenCircuit() {
        String ep = "http://endpoint-a";
        // 3 次连续失败触发熔断
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);
        assertTrue(tracker.isAvailable(ep), "Still available after 2 failures");
        tracker.recordFailure(ep);
        assertFalse(tracker.isAvailable(ep), "Should be OPEN after 3 failures");
    }

    @Test
    void successResetsToClosed() {
        String ep = "http://endpoint-a";
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);
        tracker.recordSuccess(ep); // 重置
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);
        assertTrue(tracker.isAvailable(ep), "Should still be CLOSED after reset");
    }

    @Test
    void openCircuitTransitionsToHalfOpen() throws InterruptedException {
        String ep = "http://endpoint-a";
        // 触发 OPEN
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);
        assertFalse(tracker.isAvailable(ep), "Should be OPEN");

        // 等待 openDuration 后应转为 HALF_OPEN
        Thread.sleep(300);
        assertTrue(tracker.isAvailable(ep), "Should be HALF_OPEN after openDuration");
    }

    @Test
    void halfOpenSuccessClosesCircuit() throws InterruptedException {
        String ep = "http://endpoint-a";
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);
        assertFalse(tracker.isAvailable(ep));

        Thread.sleep(300); // 等 HALF_OPEN
        assertTrue(tracker.isAvailable(ep));
        tracker.recordSuccess(ep); // 试探成功，回到 CLOSED
        assertTrue(tracker.isAvailable(ep), "Should be CLOSED after successful probe");
    }

    @Test
    void halfOpenFailureReOpensCircuit() throws InterruptedException {
        String ep = "http://endpoint-a";
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);
        tracker.recordFailure(ep);

        Thread.sleep(300); // 等 HALF_OPEN
        assertTrue(tracker.isAvailable(ep));
        tracker.recordFailure(ep); // HALF_OPEN 中再次失败
        assertFalse(tracker.isAvailable(ep), "Should be re-OPENED after half-open failure");
    }

    @Test
    void differentEndpointsIndependent() {
        String epA = "http://endpoint-a";
        String epB = "http://endpoint-b";
        // A 熔断不影响 B
        tracker.recordFailure(epA);
        tracker.recordFailure(epA);
        tracker.recordFailure(epA);
        assertFalse(tracker.isAvailable(epA));
        assertTrue(tracker.isAvailable(epB));
    }
}

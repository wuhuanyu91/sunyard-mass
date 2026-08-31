package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模型端点健康追踪与熔断器（§8 已知限制 — 熔断/故障转移）：
 * 每个 endpointUrl 独立维护状态机 CLOSED → OPEN → HALF_OPEN → CLOSED。
 * 连续失败达阈值时熔断（OPEN），拒绝路由；经过 openDuration 后进入 HALF_OPEN 试探恢复。
 */
@Service
public class ModelHealthTracker {

    private static final Logger log = LoggerFactory.getLogger(ModelHealthTracker.class);

    private final MasProperties props;
    private final ConcurrentHashMap<String, EndpointState> states = new ConcurrentHashMap<>();

    public ModelHealthTracker(MasProperties props) {
        this.props = props;
    }

    /** @return true 端点可用（CLOSED 或 HALF_OPEN 且试探次数未满） */
    public boolean isAvailable(String endpointUrl) {
        EndpointState state = states.get(endpointUrl);
        if (state == null) {
            return true;
        }
        return state.isAvailable();
    }

    /** 记录一次成功调用，重置失败计数 */
    public void recordSuccess(String endpointUrl) {
        states.compute(endpointUrl, (k, v) -> {
            EndpointState s = (v != null) ? v : new EndpointState();
            s.resetToClosed();
            return s;
        });
    }

    /** 记录一次失败调用，可能触发状态转换 */
    public void recordFailure(String endpointUrl) {
        states.compute(endpointUrl, (k, v) -> {
            EndpointState s = (v != null) ? v : new EndpointState();
            s.recordFailure();
            if (s.state == CircuitState.OPEN) {
                log.warn("Circuit OPEN for endpoint {}, failover will be used", endpointUrl);
            }
            return s;
        });
    }

    // ---- 内部状态机 ----

    enum CircuitState {
        CLOSED, OPEN, HALF_OPEN
    }

    class EndpointState {
        volatile CircuitState state = CircuitState.CLOSED;
        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        volatile Instant openedAt;
        final AtomicInteger halfOpenAttempts = new AtomicInteger(0);

        boolean isAvailable() {
            return switch (state) {
                case CLOSED -> true;
                case OPEN -> {
                    // 检查是否已过 openDuration，转入 HALF_OPEN
                    Duration openDuration = props.getCircuitBreaker().getOpenDuration();
                    if (openedAt != null && Instant.now().isAfter(openedAt.plus(openDuration))) {
                        state = CircuitState.HALF_OPEN;
                        halfOpenAttempts.set(0);
                        log.info("Circuit HALF_OPEN for endpoint, attempting recovery");
                        yield true;
                    }
                    yield false;
                }
                case HALF_OPEN -> halfOpenAttempts.incrementAndGet() <= props.getCircuitBreaker().getHalfOpenMaxAttempts();
            };
        }

        void recordFailure() {
            int failures = consecutiveFailures.incrementAndGet();
            if (state == CircuitState.HALF_OPEN) {
                // HALF_OPEN 中再次失败，重新熔断
                state = CircuitState.OPEN;
                openedAt = Instant.now();
                halfOpenAttempts.set(0);
                log.warn("Circuit re-OPEN (half-open probe failed)");
            } else if (failures >= props.getCircuitBreaker().getFailureThreshold()) {
                state = CircuitState.OPEN;
                openedAt = Instant.now();
                log.warn("Circuit OPEN after {} consecutive failures", failures);
            }
        }

        void resetToClosed() {
            state = CircuitState.CLOSED;
            consecutiveFailures.set(0);
            halfOpenAttempts.set(0);
            openedAt = null;
        }
    }
}

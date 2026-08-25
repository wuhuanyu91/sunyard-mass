package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L1 频率限制：Bucket4j 内存滑动窗口，按 X-User-Id 分桶（附录 G.7）。
 */
@Service
public class RateLimitService {

    private final MasProperties props;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitService(MasProperties props) {
        this.props = props;
    }

    /** @return true 放行，false 超限 */
    public boolean tryAcquire(String userId) {
        Bucket bucket = buckets.computeIfAbsent(userId, this::newBucket);
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(String userId) {
        int qps = Math.max(1, props.getRateLimit().getPerUserQps());
        return Bucket.builder()
                .addLimit(Bandwidth.simple(qps, Duration.ofSeconds(1)))
                .build();
    }
}

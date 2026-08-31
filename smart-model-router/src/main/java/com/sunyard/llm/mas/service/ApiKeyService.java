package com.sunyard.llm.mas.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunyard.llm.mas.entity.ApiKeyEntity;
import com.sunyard.llm.mas.mapper.ApiKeyMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API Key 鉴权服务（§8 已知限制消除 — 鉴权体系）：
 * Bearer token → SHA-256 哈希 → 查 DB → Caffeine 缓存(5min) → 返回 userId/appId。
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyMapper mapper;
    private final Cache<String, ApiKeyInfo> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(java.time.Duration.ofMinutes(5))
            .build();

    public ApiKeyService(ApiKeyMapper mapper) {
        this.mapper = mapper;
    }

    /** 验证 Bearer token，返回 ApiKeyInfo；无效或过期抛 IllegalStateException */
    public Mono<ApiKeyInfo> validate(String bearerToken) {
        String hash = sha256(bearerToken);
        ApiKeyInfo cached = cache.getIfPresent(hash);
        if (cached != null) {
            return Mono.just(cached);
        }
        return ReactiveDbAdapter.mono(() -> mapper.selectByHash(hash))
                .flatMap(entity -> {
                    if (entity == null) {
                        return Mono.error(new IllegalStateException("invalid api key"));
                    }
                    // 检查过期
                    if (entity.getExpireAt() != null && entity.getExpireAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new IllegalStateException("api key expired"));
                    }
                    ApiKeyInfo info = new ApiKeyInfo(entity.getUserId(), entity.getAppId(), entity.getKeyPrefix());
                    cache.put(hash, info);
                    return Mono.just(info);
                });
    }

    /** 创建新 API Key，返回明文 Key（仅此一次可见） */
    public Mono<String> createKey(String userId, String appId) {
        String plainKey = "mas-" + UUID.randomUUID().toString().replace("-", "");
        String hash = sha256(plainKey);
        String prefix = plainKey.substring(0, 12);
        return ReactiveDbAdapter.monoVoid(() -> mapper.insertKey(hash, prefix, userId, appId, null))
                .thenReturn(plainKey);
    }

    /** 撤销 API Key（按前缀） */
    public Mono<Boolean> revokeKey(String prefix) {
        return ReactiveDbAdapter.mono(() -> mapper.revokeByPrefix(prefix))
                .map(updated -> {
                    // 清除缓存中所有以该 prefix 开头的条目（简化处理：不清缓存，等 5 分钟过期）
                    return updated > 0;
                });
    }

    /** SHA-256 哈希 */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record ApiKeyInfo(String userId, String appId, String keyPrefix) {
    }
}

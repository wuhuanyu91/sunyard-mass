package com.sunyard.llm.mas.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.entity.ApiKeyEntity;
import com.sunyard.llm.mas.mapper.ApiKeyMapper;
import com.sunyard.llm.mas.mapper.QuotaMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API Key 鉴权服务（§8 鉴权体系 + §1.4 自助申请增强）：
 * Bearer token → SHA-256 哈希 → 查 DB → Caffeine 缓存(5min) → 返回 userId/appId。
 * 增强：支持元数据创建、配额自动关联、Key 列表/轮换/批量吊销/用量查询。
 */
@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private final ApiKeyMapper mapper;
    private final QuotaMapper quotaMapper;
    private final MasProperties props;
    private final Cache<String, ApiKeyInfo> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(java.time.Duration.ofMinutes(5))
            .build();

    public ApiKeyService(ApiKeyMapper mapper, QuotaMapper quotaMapper, MasProperties props) {
        this.mapper = mapper;
        this.quotaMapper = quotaMapper;
        this.props = props;
    }

    /** 验证 Bearer token，返回 ApiKeyInfo；无效或过期抛 IllegalStateException */
    public Mono<ApiKeyInfo> validate(String bearerToken) {
        String hash = sha256(bearerToken);
        ApiKeyInfo cached = cache.getIfPresent(hash);
        if (cached != null) {
            return Mono.just(cached);
        }
        return ReactiveDbAdapter.mono(() -> mapper.selectByHash(hash))
                .switchIfEmpty(Mono.error(new IllegalStateException("invalid api key")))
                .flatMap(entity -> {
                    if (entity.getExpireAt() != null && entity.getExpireAt().isBefore(LocalDateTime.now())) {
                        return Mono.error(new IllegalStateException("api key expired"));
                    }
                    ApiKeyInfo info = new ApiKeyInfo(entity.getUserId(), entity.getAppId(), entity.getKeyPrefix());
                    cache.put(hash, info);
                    return Mono.just(info);
                });
    }

    /** 创建新 API Key（兼容旧签名，无元数据） */
    public Mono<String> createKey(String userId, String appId) {
        return createKeyFull(userId, appId, null, null, null, null, null, "default", "admin");
    }

    /**
     * §1.4 增强版创建 API Key：携带完整元数据 + 自动关联配额。
     *
     * @return 明文 Key（仅此一次可见）
     */
    public Mono<String> createKeyFull(String userId, String appId,
                                      String teamName, String agentName, String agentType,
                                      String purpose, Integer expireDays,
                                      String quotaTier, String createdBy) {
        String plainKey = "mas-" + UUID.randomUUID().toString().replace("-", "");
        String hash = sha256(plainKey);
        String prefix = plainKey.substring(0, 12);
        String tier = quotaTier != null ? quotaTier : "default";
        String creator = createdBy != null ? createdBy : "admin";

        // 计算过期时间
        LocalDateTime expireAt = null;
        if (expireDays != null && expireDays > 0) {
            expireAt = LocalDateTime.now().plusDays(expireDays);
        }

        final LocalDateTime finalExpireAt = expireAt;
        return ReactiveDbAdapter.monoVoid(() -> mapper.insertKeyFull(
                        hash, prefix, userId, appId, finalExpireAt,
                        teamName, agentName, agentType, purpose, tier, creator))
                .then(Mono.defer(() -> provisionQuota(userId, tier)))
                .thenReturn(plainKey);
    }

    /** §1.4 自动关联配额：按档位写入 mas_token_quota */
    private Mono<Void> provisionQuota(String userId, String tier) {
        MasProperties.Quota.TierDef tierDef = props.getQuota().resolveTier(tier);
        // unlimited 档位（-1）不写配额行，QuotaService 中无配额行即不限制
        if (tierDef.getPerMinute() < 0 && tierDef.getPerDay() < 0) {
            return Mono.empty();
        }
        Instant now = Instant.now();
        Instant minuteReset = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES);
        LocalDateTime minuteResetDt = LocalDateTime.ofInstant(minuteReset, ZoneId.systemDefault());
        LocalDateTime dayResetDt = LocalDateTime.now(ZoneId.systemDefault())
                .toLocalDate().plusDays(1).atStartOfDay();
        return ReactiveDbAdapter.monoVoid(() ->
                        quotaMapper.upsertQuota(userId, "minute", tierDef.getPerMinute(), minuteResetDt))
                .then(ReactiveDbAdapter.monoVoid(() ->
                        quotaMapper.upsertQuota(userId, "day", tierDef.getPerDay(), dayResetDt)));
    }

    /** 撤销 API Key（按前缀） */
    public Mono<Boolean> revokeKey(String prefix) {
        return ReactiveDbAdapter.mono(() -> mapper.revokeByPrefix(prefix))
                .map(updated -> {
                    return updated > 0;
                });
    }

    // ---- §1.4 自助申请增强方法 ----

    /** §1.4 Key 列表查询 */
    public Mono<List<Map<String, Object>>> listKeys(String teamName, String userId,
                                                     Integer status, String agentType) {
        return ReactiveDbAdapter.mono(() -> {
            List<ApiKeyEntity> entities = mapper.listKeys(teamName, userId, status, agentType);
            List<Map<String, Object>> result = new ArrayList<>();
            for (ApiKeyEntity e : entities) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key_prefix", e.getKeyPrefix());
                row.put("user_id", e.getUserId());
                row.put("app_id", e.getAppId());
                row.put("status", e.getStatus());
                row.put("expire_at", e.getExpireAt());
                row.put("created_at", e.getCreatedAt());
                row.put("team_name", e.getTeamName());
                row.put("agent_name", e.getAgentName());
                row.put("agent_type", e.getAgentType());
                row.put("purpose", e.getPurpose());
                row.put("quota_tier", e.getQuotaTier());
                row.put("created_by", e.getCreatedBy());
                result.add(row);
            }
            return result;
        });
    }

    /**
     * §1.4 Key 轮换：生成新 Key 继承旧 Key 的身份/元数据/配额，旧 Key 进入 grace period。
     *
     * @return 新 Key 明文（仅此一次可见）
     */
    public Mono<String> rotateKey(String oldPrefix) {
        return ReactiveDbAdapter.mono(() -> mapper.selectByPrefix(oldPrefix))
                .switchIfEmpty(Mono.error(new IllegalStateException("api key not found: " + oldPrefix)))
                .flatMap(old -> {
                    String newPlainKey = "mas-" + UUID.randomUUID().toString().replace("-", "");
                    String newHash = sha256(newPlainKey);
                    String newPrefix = newPlainKey.substring(0, 12);

                    // 计算 grace period 过期时间
                    LocalDateTime graceExpiry = LocalDateTime.now()
                            .plus(props.getQuota().getRotateGracePeriod());

                    return ReactiveDbAdapter.monoVoid(() -> mapper.insertKeyFull(
                                    newHash, newPrefix, old.getUserId(), old.getAppId(), old.getExpireAt(),
                                    old.getTeamName(), old.getAgentName(), old.getAgentType(),
                                    old.getPurpose(),
                                    old.getQuotaTier() != null ? old.getQuotaTier() : "default",
                                    "rotate"))
                            .then(ReactiveDbAdapter.mono(() -> mapper.setGraceExpiry(oldPrefix, graceExpiry)))
                            .thenReturn(newPlainKey);
                });
    }

    /** §1.4 批量吊销 */
    public Mono<Integer> batchRevoke(String teamName, String userId) {
        if ((teamName == null || teamName.isBlank()) && (userId == null || userId.isBlank())) {
            return Mono.just(0);
        }
        return ReactiveDbAdapter.mono(() -> mapper.batchRevoke(teamName, userId));
    }

    /** §1.4 用量查询：返回今日/本周/本月的 token 消耗、调用次数、缓存命中率 */
    public Mono<Map<String, Object>> getUsage(String userId) {
        return ReactiveDbAdapter.mono(() -> {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("user_id", userId);

            LocalDateTime now = LocalDateTime.now();
            usage.put("today", buildUsageWindow(mapper.usageSummary(userId, now.toLocalDate().atStartOfDay())));
            usage.put("this_week", buildUsageWindow(mapper.usageSummary(userId, now.toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay())));
            usage.put("this_month", buildUsageWindow(mapper.usageSummary(userId, now.toLocalDate().withDayOfMonth(1).atStartOfDay())));

            return usage;
        });
    }

    private Map<String, Object> buildUsageWindow(Map<String, Object> raw) {
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("total_tokens", toLong(raw.get("total_tokens")));
        window.put("call_count", toLong(raw.get("call_count")));
        window.put("cache_hits", toLong(raw.get("cache_hits")));
        return window;
    }

    private static long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number n) return n.longValue();
        return 0L;
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

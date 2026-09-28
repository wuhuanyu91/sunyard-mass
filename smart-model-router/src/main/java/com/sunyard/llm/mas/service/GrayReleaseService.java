package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.ModelLifecycleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 灰度发布运行时（POC 验收第 9 问：模型能否灰度/A/B/快速回滚）。
 * <p>
 * 【改造背景】此前 mas_model_release 只有管理面 CRUD（建单/调比例/回滚改状态），
 * 路由管线完全不读这张表 —— 灰度"配置存在但运行时零效果"，回滚也只在页面上看得见。
 * 本服务把进行中的发布单接入 L3 选模型环节：
 * <ul>
 *   <li>GRAYING：按 gray_percent 做确定性分桶（同一用户/应用始终落在同一侧，不会忽大忽小）；</li>
 *   <li>FULL：全量切到目标版本；</li>
 *   <li>ROLLBACK / ROLLING_BACK / ABORTED：不在快照内，撤回在一次请求内立即生效。</li>
 * </ul>
 * 30s 快照缓存，避免每次请求查库；映射不到目标模型时保持原模型（fail-safe，不影响可用性）。
 */
@Service
public class GrayReleaseService {

    private static final Logger log = LoggerFactory.getLogger(GrayReleaseService.class);
    private static final long CACHE_TTL_MS = 30_000L;

    private final ModelLifecycleMapper lifecycleMapper;
    private final TenantService tenantService;

    private volatile Map<String, Release> snapshot = Map.of();
    private volatile long expireAt = 0L;

    public GrayReleaseService(ModelLifecycleMapper lifecycleMapper, TenantService tenantService) {
        this.lifecycleMapper = lifecycleMapper;
        this.tenantService = tenantService;
    }

    /** 一条生效中的发布单 */
    public record Release(String releaseId, String modelId, String fromVersion, String toVersion,
                          int grayPercent, String grayScope, String status) {

        public boolean full() {
            return "FULL".equals(status);
        }
    }

    /** 取该模型当前生效的发布单（无则 null） */
    public Release of(String modelId) {
        if (modelId == null || modelId.isBlank()) return null;
        return snapshot().get(modelId);
    }

    /**
     * 本次请求是否命中灰度。
     * 分桶键优先取 userId（同一用户稳定命中），无用户身份时退到 appId，再退到 traceId（单次随机）。
     */
    public boolean hit(Release release, String userId, String appId, String traceId) {
        if (release == null) return false;
        if (release.full() || release.grayPercent() >= 100) return true;
        if (release.grayPercent() <= 0) return false;
        if (!inScope(release.grayScope(), userId, appId)) return false;
        String bucketKey = firstNonBlank(userId, appId, traceId, "");
        if (bucketKey.isEmpty()) return false;
        return bucket(bucketKey, release.releaseId()) < release.grayPercent();
    }

    /**
     * 灰度范围（gray_scope）匹配：支持 APP:xxx / TENANT:xxx / USER:xxx，多个用逗号分隔；
     * 为空表示不限范围。范围不匹配时不参与灰度（仍在原版本）。
     */
    private boolean inScope(String scope, String userId, String appId) {
        if (scope == null || scope.isBlank()) return true;
        String tenant = tenantService == null || appId == null || appId.isBlank()
                ? "" : tenantService.resolveTenantByApp(appId);
        boolean any = false;
        for (String item : scope.split("[,;]")) {
            String s = item.trim();
            if (s.isEmpty()) continue;
            any = true;
            int idx = s.indexOf(':');
            String type = idx > 0 ? s.substring(0, idx).trim().toUpperCase() : "APP";
            String value = idx > 0 ? s.substring(idx + 1).trim() : s;
            if ("USER".equals(type) && value.equalsIgnoreCase(userId)) return true;
            if ("APP".equals(type) && value.equalsIgnoreCase(appId)) return true;
            if ("TENANT".equals(type) && value.equalsIgnoreCase(tenant)) return true;
        }
        return !any;
    }

    /** 稳定分桶：同一 key 在同一发布单下恒定落在 0-99 的某个位置（floorMod 规避负数边界） */
    private int bucket(String key, String releaseId) {
        return Math.floorMod((releaseId + "#" + key).hashCode(), 100);
    }

    /** 管理面改动发布单后调用，立即失效快照 */
    public void evict() {
        expireAt = 0L;
    }

    private Map<String, Release> snapshot() {
        long now = System.currentTimeMillis();
        if (expireAt > now && !snapshot.isEmpty()) {
            return snapshot;
        }
        Map<String, Release> fresh = new HashMap<>();
        try {
            List<Map<String, Object>> rows = lifecycleMapper == null ? List.of() : lifecycleMapper.listActiveReleases();
            for (Map<String, Object> r : rows) {
                String modelId = str(r.get("model_id"));
                if (modelId.isEmpty()) continue;
                // 同一模型存在多张进行中单据时，listActiveReleases 已按创建时间倒序，保留第一条（最新）
                if (fresh.containsKey(modelId)) continue;
                fresh.put(modelId, new Release(
                        str(r.get("release_id")),
                        modelId,
                        str(r.get("from_version")),
                        str(r.get("to_version")),
                        toInt(r.get("gray_percent"), 0),
                        str(r.get("gray_scope")),
                        str(r.get("status"))));
            }
        } catch (Exception ex) {
            log.warn("Gray release snapshot degraded (fail-safe): {}", ex.getMessage());
            return snapshot; // 保留上一次快照，绝不因为查库失败影响主链路
        }
        snapshot = fresh;
        expireAt = now + CACHE_TTL_MS;
        return fresh;
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    private static int toInt(Object v, int def) {
        if (v == null) return def;
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}

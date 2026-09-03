package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.AppEntity;
import com.sunyard.llm.mas.mapper.AppMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 应用管理服务（§1.5 应用身份统一管控）：
 * 提供应用注册、查询、启用/停用等功能。
 * 创建应用时自动生成 app_id 并关联生成 API Key。
 */
@Service
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);

    private final AppMapper appMapper;
    private final ApiKeyService apiKeyService;

    public AppService(AppMapper appMapper, ApiKeyService apiKeyService) {
        this.appMapper = appMapper;
        this.apiKeyService = apiKeyService;
    }

    /** 应用列表查询 */
    public Mono<List<Map<String, Object>>> listApps(String status, String deptId) {
        Integer statusInt = parseStatus(status);
        return ReactiveDbAdapter.mono(() -> {
            List<AppEntity> entities = appMapper.listApps(statusInt, deptId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (AppEntity e : entities) {
                result.add(toMap(e));
            }
            return result;
        });
    }

    /** 应用详情 */
    public Mono<Map<String, Object>> getApp(String appId) {
        return ReactiveDbAdapter.mono(() -> {
            AppEntity e = appMapper.selectByAppId(appId);
            if (e == null) return null;
            return toMap(e);
        });
    }

    /**
     * 创建应用：自动生成 app_id（APP-XXXX 格式），并关联生成 API Key。
     * 返回应用信息 + 明文 API Key（仅首次可见）。
     */
    public Mono<Map<String, Object>> createApp(Map<String, Object> body) {
        String appId = "APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String appName = strOrNull(body, "app_name");
        String appNameEn = strOrNull(body, "app_name_en");
        String deptId = strOrNull(body, "dept_id");
        String ownerId = strOrNull(body, "owner_id");
        String ownerEmail = strOrNull(body, "owner_email");
        String slaLevel = strOr(body, "sla_level", "P1");
        String dataLevel = strOr(body, "data_level", "L2");
        Long monthQuota = longOrNull(body, "month_quota");
        String description = strOrNull(body, "description");

        if (appName == null || appName.isBlank()) {
            return Mono.error(new IllegalArgumentException("app_name is required"));
        }
        if (deptId == null || deptId.isBlank()) {
            return Mono.error(new IllegalArgumentException("dept_id is required"));
        }
        if (ownerId == null || ownerId.isBlank()) {
            return Mono.error(new IllegalArgumentException("owner_id is required"));
        }

        final String finalAppName = appName;
        final String finalAppNameEn = appNameEn;
        final String finalDeptId = deptId;
        final String finalOwnerId = ownerId;
        final String finalOwnerEmail = ownerEmail;
        final String finalSlaLevel = slaLevel;
        final String finalDataLevel = dataLevel;
        final Long finalMonthQuota = monthQuota;
        final String finalDescription = description;

        // 1. 插入应用记录
        return ReactiveDbAdapter.monoVoid(() -> appMapper.insertApp(
                        appId, finalAppName, finalAppNameEn, finalDeptId, finalOwnerId,
                        finalOwnerEmail, finalSlaLevel, finalDataLevel, finalMonthQuota, finalDescription))
                // 2. 关联生成 API Key
                .then(apiKeyService.createKeyFull(finalOwnerId, appId,
                        finalDeptId, finalAppName, null,
                        finalDescription, null, "default", "admin"))
                .map(plainKey -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("app_id", appId);
                    result.put("app_name", finalAppName);
                    result.put("app_name_en", finalAppNameEn);
                    result.put("dept_id", finalDeptId);
                    result.put("owner_id", finalOwnerId);
                    result.put("owner_email", finalOwnerEmail);
                    result.put("sla_level", finalSlaLevel);
                    result.put("data_level", finalDataLevel);
                    result.put("status", 1);
                    result.put("month_quota", finalMonthQuota);
                    result.put("description", finalDescription);
                    result.put("api_key", plainKey);
                    result.put("message", "Save this API Key, it will not be shown again");
                    return result;
                });
    }

    /** 更新应用信息 */
    public Mono<Map<String, Object>> updateApp(String appId, Map<String, Object> body) {
        String appName = strOrNull(body, "app_name");
        String appNameEn = strOrNull(body, "app_name_en");
        String deptId = strOrNull(body, "dept_id");
        String ownerId = strOrNull(body, "owner_id");
        String ownerEmail = strOrNull(body, "owner_email");
        String slaLevel = strOr(body, "sla_level", "P1");
        String dataLevel = strOr(body, "data_level", "L2");
        Long monthQuota = longOrNull(body, "month_quota");
        String description = strOrNull(body, "description");

        return ReactiveDbAdapter.monoVoid(() -> appMapper.updateApp(
                        appId, appName, appNameEn, deptId, ownerId, ownerEmail,
                        slaLevel, dataLevel, monthQuota, description))
                .then(Mono.defer(() -> getApp(appId)))
                .switchIfEmpty(Mono.error(new IllegalStateException("app not found: " + appId)));
    }

    /** 启用/停用应用（toggle） */
    public Mono<Map<String, Object>> toggleApp(String appId) {
        return ReactiveDbAdapter.mono(() -> appMapper.selectByAppId(appId))
                .switchIfEmpty(Mono.error(new IllegalStateException("app not found: " + appId)))
                .flatMap(entity -> {
                    int newStatus;
                    if (entity.getStatus() == null || entity.getStatus() == 1) {
                        newStatus = 2; // 已启用 → 已停用
                    } else {
                        newStatus = 1; // 其他状态 → 已启用
                    }
                    final int fs = newStatus;
                    return ReactiveDbAdapter.monoVoid(() -> appMapper.updateStatus(appId, fs))
                            .then(Mono.defer(() -> getApp(appId)));
                });
    }

    /** 删除应用 */
    public Mono<Map<String, Object>> deleteApp(String appId) {
        return ReactiveDbAdapter.mono(() -> {
            AppEntity e = appMapper.selectByAppId(appId);
            if (e == null) return Map.<String, Object>of("error", "app not found: " + appId);
            // 逻辑删除：设置 status = -1
            appMapper.updateStatus(appId, -1);
            return Map.<String, Object>of("deleted", true, "app_id", appId);
        });
    }

    // ---- 工具方法 ----

    private Map<String, Object> toMap(AppEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app_id", e.getAppId());
        m.put("app_name", e.getAppName());
        m.put("app_name_en", e.getAppNameEn());
        m.put("dept_id", e.getDeptId());
        m.put("owner_id", e.getOwnerId());
        m.put("owner_email", e.getOwnerEmail());
        m.put("sla_level", e.getSlaLevel());
        m.put("data_level", e.getDataLevel());
        m.put("status", e.getStatus());
        m.put("month_quota", e.getMonthQuota());
        m.put("description", e.getDescription());
        m.put("approved_by", e.getApprovedBy());
        m.put("approved_at", e.getApprovedAt());
        m.put("created_at", e.getCreatedAt());
        m.put("updated_at", e.getUpdatedAt());
        return m;
    }

    private static Integer parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return Integer.parseInt(status);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String strOr(Map<String, Object> body, String key, String defaultVal) {
        Object v = body.get(key);
        return v != null ? v.toString() : defaultVal;
    }

    private static String strOrNull(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static Long longOrNull(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

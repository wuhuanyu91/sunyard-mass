package com.sunyard.llm.mas.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunyard.llm.mas.entity.TenantEntity;
import com.sunyard.llm.mas.mapper.TenantMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户服务（公告二-1 多租户场景 + 二-2 租户隔离）：
 * 1. 租户与「部门↔租户 / 应用↔租户」映射全部配置化，消除 Java 硬编码 switch；
 * 2. 提供 resolveTenantByApp / resolveTenantByDept 供采集与计量链路调用（带 60s 缓存）；
 * 3. 停用租户立即收回（校验租户状态，fail-closed）。
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);
    private static final String MODULE = "tenant";
    private static final String UNKNOWN = "UNKNOWN";

    private final TenantMapper tenantMapper;
    private final OpLogService opLogService;

    /** 映射缓存：避免每次请求击穿 DB（60s 刷新） */
    private final Cache<String, String> appTenantCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(2000).build();
    private final Cache<String, String> deptTenantCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60)).maximumSize(2000).build();

    public TenantService(TenantMapper tenantMapper, OpLogService opLogService) {
        this.tenantMapper = tenantMapper;
        this.opLogService = opLogService;
    }

    // ---------------- 租户 CRUD ----------------

    public Mono<List<Map<String, Object>>> listTenants() {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> tenants = tenantMapper.listTenants();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> t : tenants) {
                Map<String, Object> row = new LinkedHashMap<>(t);
                row.put("enabled", Integer.valueOf(1).equals(t.get("status")));
                result.add(row);
            }
            return result;
        });
    }

    public Mono<Map<String, Object>> createTenant(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String tenantId = str(body.get("tenantId"));
            if (tenantId.isEmpty()) throw new IllegalArgumentException("tenantId 必填");
            tenantMapper.insertTenant(tenantId, str(body.getOrDefault("tenantName", tenantId)),
                    intVal(body.get("status"), 1), str(body.get("isolationMode")),
                    longVal(body.get("quotaTokens")), str(body.get("contact")));
            return tenantId;
        }).flatMap(id -> opLogService.record(MODULE, "新增租户", operator, id, "创建租户 " + id));
    }

    public Mono<Map<String, Object>> updateTenant(String tenantId, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            tenantMapper.updateTenant(tenantId, str(body.get("tenantName")),
                    body.get("status") == null ? null : intVal(body.get("status"), 1),
                    str(body.get("isolationMode")), longVal(body.get("quotaTokens")), str(body.get("contact")));
            invalidate();
            return tenantId;
        }).flatMap(id -> opLogService.record(MODULE, "修改租户", operator, id, "更新租户配置"));
    }

    /** 启用/停用：停用即收回该租户的模型与数据访问权限 */
    public Mono<Map<String, Object>> setTenantStatus(String tenantId, boolean enabled, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            tenantMapper.updateTenant(tenantId, null, enabled ? 1 : 0, null, null, null);
            invalidate();
            log.warn("tenant {} status changed to enabled={}", tenantId, enabled);
            return tenantId;
        }).flatMap(id -> opLogService.record(MODULE, enabled ? "启用租户" : "停用租户", operator, id,
                enabled ? "恢复租户权限" : "停用租户，收回模型与数据权限"));
    }

    public Mono<Map<String, Object>> deleteTenant(String tenantId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            tenantMapper.deleteTenant(tenantId);
            invalidate();
            return tenantId;
        }).flatMap(id -> opLogService.record(MODULE, "删除租户", operator, id, "删除租户 " + id));
    }

    // ---------------- 映射配置化 ----------------

    public Mono<List<Map<String, Object>>> listDeptTenants() {
        return ReactiveDbAdapter.mono(tenantMapper::listDeptTenants);
    }

    public Mono<Map<String, Object>> upsertDeptTenant(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String deptId = str(body.get("deptId"));
            String tenantId = str(body.get("tenantId"));
            if (deptId.isEmpty() || tenantId.isEmpty()) throw new IllegalArgumentException("deptId/tenantId 必填");
            tenantMapper.upsertDeptTenant(deptId, str(body.get("deptName")), tenantId);
            invalidate();
            return deptId + "->" + tenantId;
        }).flatMap(t -> opLogService.record(MODULE, "部门租户映射", operator, str(body.get("deptId")), "映射 " + t));
    }

    public Mono<List<Map<String, Object>>> listAppTenants() {
        return ReactiveDbAdapter.mono(tenantMapper::listAppTenants);
    }

    public Mono<Map<String, Object>> upsertAppTenant(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String appId = str(body.get("appId"));
            String tenantId = str(body.get("tenantId"));
            if (appId.isEmpty() || tenantId.isEmpty()) throw new IllegalArgumentException("appId/tenantId 必填");
            tenantMapper.upsertAppTenant(appId, tenantId);
            appTenantCache.invalidate(appId);
            return appId + "->" + tenantId;
        }).flatMap(t -> opLogService.record(MODULE, "应用租户映射", operator, str(body.get("appId")), "映射 " + t));
    }

    // ---------------- 运行时解析（供管线 / 计量调用） ----------------

    /** 应用 → 租户（替代 CallLogService.appToTenant 硬编码 switch） */
    public String resolveTenantByApp(String appId) {
        if (appId == null || appId.isEmpty()) return UNKNOWN;
        return appTenantCache.get(appId, key -> {
            try {
                String t = tenantMapper.tenantOfApp(key);
                return t != null ? t : UNKNOWN;
            } catch (Exception e) {
                log.warn("resolve tenant by app failed, app={}", key, e);
                return UNKNOWN;
            }
        });
    }

    /** 部门 → 租户（替代 MeteringService.deptToTenant 硬编码 switch） */
    public String resolveTenantByDept(String deptId) {
        if (deptId == null || deptId.isEmpty()) return UNKNOWN;
        return deptTenantCache.get(deptId, key -> {
            try {
                String t = tenantMapper.tenantOfDept(key);
                return t != null ? t : UNKNOWN;
            } catch (Exception e) {
                log.warn("resolve tenant by dept failed, dept={}", key, e);
                return UNKNOWN;
            }
        });
    }

    /**
     * 租户隔离校验（二-2）：停用租户一律拒绝。
     * fail-closed：DB 异常或查不到租户时返回 false。
     */
    public boolean isTenantActive(String tenantId) {
        if (tenantId == null || tenantId.isEmpty() || UNKNOWN.equals(tenantId)) return false;
        try {
            Map<String, Object> t = tenantMapper.selectTenant(tenantId);
            return t != null && Integer.valueOf(1).equals(t.get("status"));
        } catch (Exception e) {
            log.error("tenant check failed, tenant={}", tenantId, e);
            return false;
        }
    }

    /** 租户 → 主部门（反向映射，供计量展示） */
    public String deptOfTenant(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) return "";
        try {
            for (Map<String, Object> m : tenantMapper.listDeptTenants()) {
                if (tenantId.equals(String.valueOf(m.get("tenant_id")))) {
                    return String.valueOf(m.get("dept_id"));
                }
            }
        } catch (Exception e) {
            log.warn("dept of tenant resolve failed: {}", tenantId, e);
        }
        return "";
    }

    /** 租户显示名（带缓存，供计量/账单展示） */
    public String tenantName(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) return null;
        try {
            Map<String, Object> t = tenantMapper.selectTenant(tenantId);
            return t == null ? null : String.valueOf(t.get("tenant_name"));
        } catch (Exception e) {
            return null;
        }
    }

    /** 租户级配额（二-2：配额应在租户层有闸口） */
    public Long tenantQuota(String tenantId) {
        try {
            Map<String, Object> t = tenantMapper.selectTenant(tenantId);
            if (t == null || t.get("quota_tokens") == null) return null;
            return ((Number) t.get("quota_tokens")).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    public void invalidate() {
        appTenantCache.invalidateAll();
        deptTenantCache.invalidateAll();
    }

    /** 供运维校验：返回实体列表（MyBatis-Plus BaseMapper 能力保留） */
    public List<TenantEntity> selectAll() {
        return tenantMapper.selectList(null);
    }

    // ---------------- helpers ----------------

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static Integer intVal(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static Long longVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}

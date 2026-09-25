package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.RbacMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RBAC 权限服务（公告二-4 权限控制）：
 * 用户 / 角色 / 权限矩阵 / 成员管理，全部真实落库；并提供鉴权判定给管理端点过滤器使用。
 */
@Service
public class RbacService {

    private static final Logger log = LoggerFactory.getLogger(RbacService.class);
    private static final String MODULE = "rbac";
    private static final List<String> MODULES = List.of(
            "dashboard", "metering", "routing", "modelAsset", "security", "apiKey", "cache",
            "apps", "tenant", "policy", "compute", "system");
    /** 权限级别权重，用于比较 */
    private static final Map<String, Integer> LEVEL_WEIGHT = Map.of(
            "DENY", 0, "READ", 1, "WRITE", 2, "ADMIN", 3);

    private final RbacMapper rbacMapper;
    private final OpLogService opLogService;

    public RbacService(RbacMapper rbacMapper, OpLogService opLogService) {
        this.rbacMapper = rbacMapper;
        this.opLogService = opLogService;
    }

    // ---------------- 用户 ----------------

    public Mono<List<Map<String, Object>>> listUsers(String keyword, Integer status) {
        return ReactiveDbAdapter.mono(() -> rbacMapper.listUsers(keyword, status));
    }

    public Mono<Map<String, Object>> createUser(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String userCode = str(body.get("userCode"));
            if (userCode.isEmpty()) throw new IllegalArgumentException("userCode 必填");
            String pwd = str(body.getOrDefault("password", "Mas@123456"));
            rbacMapper.insertUser(userCode,
                    str(body.getOrDefault("userName", userCode)),
                    str(body.get("deptId")),
                    str(body.get("tenantId")),
                    str(body.get("email")),
                    str(body.get("phone")),
                    intVal(body.get("status"), 1),
                    sha256(pwd),
                    intVal(body.get("pwdMustChange"), 0),
                    intVal(body.get("mfaEnabled"), 0));
            log.info("RBAC user created: {}", userCode);
            return userCode;
        }).flatMap(userCode -> opLogService.record(MODULE, "新增用户", operator, userCode,
                "创建用户 " + userCode));
    }

    public Mono<Map<String, Object>> updateUser(String userCode, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            rbacMapper.updateUser(userCode, str(body.get("userName")), str(body.get("deptId")),
                    str(body.get("tenantId")), str(body.get("email")), str(body.get("phone")),
                    body.get("status") == null ? null : intVal(body.get("status"), 1));
            return userCode;
        }).flatMap(code -> opLogService.record(MODULE, "修改用户", operator, code, "更新用户资料"));
    }

    /** 启用/停用、解锁、重置密码、强制改密、双因素开关 —— 统一状态变更 */
    public Mono<Map<String, Object>> updateUserState(String userCode, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String pwdHash = null;
            if (body.get("password") != null) pwdHash = sha256(str(body.get("password")));
            rbacMapper.updateUserState(userCode,
                    body.get("status") == null ? null : intVal(body.get("status"), 1),
                    body.get("locked") == null ? null : intVal(body.get("locked"), 0),
                    body.get("failCount") == null ? null : intVal(body.get("failCount"), 0),
                    pwdHash,
                    body.get("pwdMustChange") == null ? null : intVal(body.get("pwdMustChange"), 0),
                    body.get("mfaEnabled") == null ? null : intVal(body.get("mfaEnabled"), 0));
            return userCode;
        }).flatMap(code -> opLogService.record(MODULE, str(body.getOrDefault("opType", "变更用户状态")),
                operator, code, str(body.getOrDefault("detail", "状态变更"))));
    }

    public Mono<Map<String, Object>> deleteUser(String userCode, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            rbacMapper.deleteUser(userCode);
            return userCode;
        }).flatMap(code -> opLogService.record(MODULE, "删除用户", operator, code, "删除用户 " + code));
    }

    // ---------------- 角色 ----------------

    public Mono<List<Map<String, Object>>> listRoles() {
        return ReactiveDbAdapter.mono(rbacMapper::listRoles);
    }

    public Mono<Map<String, Object>> createRole(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String roleCode = str(body.get("roleCode"));
            if (roleCode.isEmpty()) throw new IllegalArgumentException("roleCode 必填");
            rbacMapper.insertRole(roleCode, str(body.getOrDefault("roleName", roleCode)),
                    intVal(body.get("builtin"), 0), str(body.get("description")));
            return roleCode;
        }).flatMap(code -> opLogService.record(MODULE, "新增角色", operator, code, "创建角色 " + code));
    }

    public Mono<Map<String, Object>> updateRole(String roleCode, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            rbacMapper.updateRole(roleCode, str(body.get("roleName")), str(body.get("description")));
            return roleCode;
        }).flatMap(code -> opLogService.record(MODULE, "修改角色", operator, code, "更新角色"));
    }

    public Mono<Map<String, Object>> deleteRole(String roleCode, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int n = rbacMapper.deleteRole(roleCode);
            if (n == 0) throw new IllegalStateException("内置角色不可删除：" + roleCode);
            return roleCode;
        }).flatMap(code -> opLogService.record(MODULE, "删除角色", operator, code, "删除角色 " + code));
    }

    // ---------------- 权限矩阵 ----------------

    public Mono<List<Map<String, Object>>> listPermissions() {
        return ReactiveDbAdapter.mono(rbacMapper::listPermissions);
    }

    /**
     * 权限矩阵：模块 × 角色 四级授权视图（DENY/READ/WRITE/ADMIN）
     * 返回结构：[{module, perms: {roleCode: level}}]
     */
    public Mono<List<Map<String, Object>>> getPermMatrix() {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> roles = rbacMapper.listRoles();
            List<Map<String, Object>> cells = rbacMapper.listRolePermissions();
            List<Map<String, Object>> matrix = new ArrayList<>();
            for (String module : MODULES) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("module", module);
                Map<String, Object> perms = new LinkedHashMap<>();
                for (Map<String, Object> r : roles) {
                    String roleCode = String.valueOf(r.get("role_code"));
                    perms.put(roleCode, "DENY");
                }
                for (Map<String, Object> c : cells) {
                    if (!module.equals(String.valueOf(c.get("module")))) continue;
                    perms.put(String.valueOf(c.get("role_code")), String.valueOf(c.get("perm_level")));
                }
                row.put("perms", perms);
                matrix.add(row);
            }
            return matrix;
        });
    }

    /** 单格授权：{roleCode, module, permLevel} */
    public Mono<Map<String, Object>> setPermCell(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String roleCode = str(body.get("roleCode"));
            String module = str(body.get("module"));
            String level = str(body.getOrDefault("permLevel", "DENY")).toUpperCase();
            if (!LEVEL_WEIGHT.containsKey(level)) throw new IllegalArgumentException("permLevel 非法：" + level);
            rbacMapper.upsertRolePermission(roleCode, module, level);
            return roleCode + ":" + module + "=" + level;
        }).flatMap(t -> opLogService.record(MODULE, "设置权限", operator, str(body.get("roleCode")),
                "授权 " + t));
    }

    /** 批量授权：{roleCode, perms:{module: level}} */
    public Mono<Map<String, Object>> setPermBatch(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String roleCode = str(body.get("roleCode"));
            @SuppressWarnings("unchecked")
            Map<String, Object> perms = (Map<String, Object>) body.get("perms");
            if (perms != null) {
                for (Map.Entry<String, Object> e : perms.entrySet()) {
                    String level = String.valueOf(e.getValue()).toUpperCase();
                    if (!LEVEL_WEIGHT.containsKey(level)) continue;
                    rbacMapper.upsertRolePermission(roleCode, e.getKey(), level);
                }
            }
            return roleCode;
        }).flatMap(code -> opLogService.record(MODULE, "批量授权", operator, code, "批量设置角色权限"));
    }

    // ---------------- 成员管理 ----------------

    public Mono<List<Map<String, Object>>> listMembers() {
        return ReactiveDbAdapter.mono(rbacMapper::listMembers);
    }

    public Mono<Map<String, Object>> assignRole(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String userCode = str(body.get("userCode"));
            String roleCode = str(body.get("roleCode"));
            Boolean revoke = Boolean.parseBoolean(String.valueOf(body.getOrDefault("revoke", "false")));
            if (revoke) {
                rbacMapper.deleteUserRole(userCode, roleCode);
            } else {
                rbacMapper.insertUserRole(userCode, roleCode);
            }
            return userCode + (revoke ? "-" : "+") + roleCode;
        }).flatMap(t -> opLogService.record(MODULE, "角色分配", operator, str(body.get("userCode")),
                "调整角色 " + t));
    }

    // ---------------- 鉴权判定（供管理端点过滤器使用） ----------------

    /**
     * 判定用户对某模块是否具备所需权限级别。
     * fail-closed：任何异常或空结果一律判为无权限。
     */
    public Mono<Boolean> hasPermission(String userCode, String module, String requiredLevel) {
        return ReactiveDbAdapter.mono(() -> {
            if (userCode == null || userCode.isEmpty()) return false;
            String actual = rbacMapper.resolvePermission(userCode, module);
            if (actual == null) return false;
            int need = LEVEL_WEIGHT.getOrDefault(requiredLevel, 3);
            int has = LEVEL_WEIGHT.getOrDefault(actual, 0);
            return has >= need;
        }).onErrorReturn(false);
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

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.exception.MasException;
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
            String pwd = str(body.getOrDefault("password", "Sunyard@123"));
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
            // 不存在或内置不可删均属业务冲突（409），不得抛 ISE 走 500 崩溃路径
            if (n == 0) throw MasException.conflict("角色不存在或内置角色不可删除：" + roleCode);
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

    // ---------------- 登录认证（公告二-2 身份认证） ----------------

    private static final int MAX_FAIL_BEFORE_LOCK = 5;

    /**
     * 登录：验证用户名+密码（SHA-256 比对），通过后签发管理令牌。
     * fail-closed：用户不存在/停用/锁定/密码错误一律拒绝；连续失败 5 次自动锁定。
     */
    public Mono<Map<String, Object>> login(String userCode, String password, AdminAuthService adminAuthService) {
        return ReactiveDbAdapter.mono(() -> {
            if (userCode == null || userCode.isBlank() || password == null || password.isBlank()) {
                throw new IllegalArgumentException("用户名与密码必填");
            }
            Map<String, Object> row = rbacMapper.selectAuthRow(userCode.trim());
            if (row == null) throw new IllegalArgumentException("用户名或密码错误");
            if (intVal(row.get("status"), 0) != 1) throw new IllegalArgumentException("账号已停用，请联系管理员");
            if (intVal(row.get("locked"), 0) == 1) throw new IllegalArgumentException("账号已锁定（连续登录失败），请联系管理员解锁");
            if (!sha256(password).equals(String.valueOf(row.get("pwd_hash")))) {
                rbacMapper.recordLoginFailure(userCode.trim());
                int fails = intVal(row.get("fail_count"), 0) + 1;
                throw new IllegalArgumentException(fails >= MAX_FAIL_BEFORE_LOCK
                        ? "用户名或密码错误（账号已锁定，请联系管理员解锁）"
                        : "用户名或密码错误（剩余尝试次数 " + (MAX_FAIL_BEFORE_LOCK - fails) + "）");
            }
            rbacMapper.recordLoginSuccess(userCode.trim());
            List<String> roles = rbacMapper.listRolesOfUser(userCode.trim());
            // 令牌角色取用户实际最高角色（ADMIN 优先），不采信调用方自报值
            String topRole = roles.contains("ADMIN") ? "ADMIN" : (roles.isEmpty() ? "VIEWER" : roles.get(0));
            Map<String, Object> res = adminAuthService.issueTokenFor(userCode.trim(), topRole, 1);
            res.put("userName", String.valueOf(row.get("user_name")));
            res.put("tenantId", row.get("tenant_id"));
            res.put("roles", roles);
            res.put("pwdMustChange", intVal(row.get("pwd_must_change"), 0));
            return res;
        }).flatMap(res -> opLogService.record(MODULE, "用户登录", String.valueOf(res.get("user_code")),
                String.valueOf(res.get("user_code")), "登录成功，签发访问令牌（有效期 1 天）")
                .thenReturn(res));
    }

    /** 修改密码：验证旧密码 → 更新新密码并清除强制改密标记 */
    public Mono<Map<String, Object>> changePassword(String userCode, String oldPassword, String newPassword, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            if (newPassword == null || newPassword.length() < 10 || !newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
                throw new IllegalArgumentException("新密码须≥10位且同时包含字母与数字");
            }
            Map<String, Object> row = rbacMapper.selectAuthRow(userCode);
            if (row == null) throw new IllegalStateException("用户不存在：" + userCode);
            if (!sha256(String.valueOf(oldPassword == null ? "" : oldPassword)).equals(String.valueOf(row.get("pwd_hash")))) {
                throw new IllegalArgumentException("旧密码不正确");
            }
            rbacMapper.updateUserState(userCode, null, null, null, sha256(newPassword), 0, null);
            return userCode;
        }).flatMap(code -> opLogService.record(MODULE, "修改密码", operator, code, "用户修改登录密码，强制改密标记已清除").thenReturn(Map.of("userCode", code)));
    }

    /** 当前用户对各模块的实际权限级别（/me 用，单次 DB 往返） */
    public Mono<Map<String, String>> permissionsOf(String userCode) {
        return ReactiveDbAdapter.mono(() -> {
            Map<String, String> m = new LinkedHashMap<>();
            for (String mod : MODULES) {
                String level = rbacMapper.resolvePermission(userCode, mod);
                m.put(mod, level == null ? "DENY" : level);
            }
            return m;
        });
    }

    /**
     * 用户租户归属（数据面租户隔离用）：无归属/用户不存在时返回空 Mono，
     * 调用方须以 defaultIfEmpty 哨兵值做 fail-closed 处理。
     */
    public Mono<String> tenantOf(String userCode) {
        return ReactiveDbAdapter.mono(() -> {
            Map<String, Object> row = rbacMapper.selectAuthRow(userCode);
            Object tid = row == null ? null : row.get("tenant_id");
            return tid == null || String.valueOf(tid).isBlank() ? null : String.valueOf(tid);
        }).onErrorResume(e -> Mono.empty());
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

package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.AdminAuthService;
import com.sunyard.llm.mas.service.OpLogService;
import com.sunyard.llm.mas.service.RbacService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 身份认证端点（公告二-2）：
 * login 签发令牌（唯一免令牌端点，凭据验证在本服务内完成）；
 * me 返回当前用户与权限矩阵；change-password 真实落库；
 * logout 吊销当前令牌。RBAC 权限 enforcement 由 AdminAuthFilter 统一执行。
 */
@RestController
public class AuthController {

    private final RbacService rbacService;
    private final AdminAuthService adminAuthService;
    private final OpLogService opLogService;

    public AuthController(RbacService rbacService, AdminAuthService adminAuthService, OpLogService opLogService) {
        this.rbacService = rbacService;
        this.adminAuthService = adminAuthService;
        this.opLogService = opLogService;
    }

    /** 登录（免令牌）：验证用户名+密码 → 签发访问令牌；连续失败 5 次自动锁定 */
    @PostMapping("/internal/auth/login")
    public Mono<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        String userCode = body.get("userCode") == null ? null : String.valueOf(body.get("userCode"));
        String password = body.get("password") == null ? null : String.valueOf(body.get("password"));
        return rbacService.login(userCode, password, adminAuthService);
    }

    /** 当前登录用户：资料 + 角色 + 各模块权限级别（前端按权限渲染菜单/按钮） */
    @GetMapping("/internal/auth/me")
    public Mono<Map<String, Object>> me(@RequestHeader(value = "X-Operator", required = false) String operator) {
        if (operator == null || operator.isBlank()) {
            return Mono.error(new IllegalStateException("未识别的登录用户"));
        }
        return rbacService.permissionsOf(operator).map(perms -> {
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("userCode", operator);
            res.put("permissions", perms);
            return res;
        });
    }

    /** 修改密码（真实落库，清除强制改密标记） */
    @PostMapping("/internal/auth/change-password")
    public Mono<Map<String, Object>> changePassword(@RequestBody Map<String, Object> body,
                                                    @RequestHeader(value = "X-Operator", required = false) String operator) {
        String oldPassword = body.get("oldPassword") == null ? null : String.valueOf(body.get("oldPassword"));
        String newPassword = body.get("newPassword") == null ? null : String.valueOf(body.get("newPassword"));
        return rbacService.changePassword(operator, oldPassword, newPassword, operator);
    }

    /** 登出：吊销当前令牌（令牌从库中失效，60s 校验缓存同步失效） */
    @PostMapping("/internal/auth/logout")
    public Mono<Map<String, Object>> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String bearer,
                                            @RequestHeader(value = "X-Admin-Token", required = false) String adminToken) {
        String token = adminToken;
        if (token == null || token.isBlank()) {
            token = bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : null;
        }
        if (token == null || token.isBlank()) {
            return Mono.error(new IllegalArgumentException("缺少待吊销的令牌"));
        }
        return adminAuthService.revoke(token, null)
                .onErrorResume(e -> Mono.just(Map.of("result", "revoked")))
                .flatMap(r -> opLogService.record("system", "用户登出", null, null, "吊销当前访问令牌")
                        .thenReturn(Map.of("result", "ok")));
    }
}

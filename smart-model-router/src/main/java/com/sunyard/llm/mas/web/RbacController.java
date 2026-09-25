package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.RbacService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * RBAC 权限控制端点（公告二-4）：用户 / 角色 / 权限矩阵 / 成员管理
 */
@RestController
public class RbacController {

    private final RbacService rbacService;

    public RbacController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    // ---------------- 用户 ----------------

    @GetMapping("/internal/rbac/users")
    public Mono<List<Map<String, Object>>> listUsers(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status) {
        return rbacService.listUsers(keyword, status);
    }

    @PostMapping("/internal/rbac/users")
    public Mono<Map<String, Object>> createUser(@RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.createUser(body, operator);
    }

    @PutMapping("/internal/rbac/users/{userCode}")
    public Mono<Map<String, Object>> updateUser(@PathVariable("userCode") String userCode,
                                                @RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.updateUser(userCode, body, operator);
    }

    /** 启用/停用、解锁、重置密码、强制改密、双因素 */
    @PatchMapping("/internal/rbac/users/{userCode}/state")
    public Mono<Map<String, Object>> updateUserState(@PathVariable("userCode") String userCode,
                                                     @RequestBody Map<String, Object> body,
                                                     @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.updateUserState(userCode, body, operator);
    }

    @DeleteMapping("/internal/rbac/users/{userCode}")
    public Mono<Map<String, Object>> deleteUser(@PathVariable("userCode") String userCode,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.deleteUser(userCode, operator);
    }

    // ---------------- 角色 ----------------

    @GetMapping("/internal/rbac/roles")
    public Mono<List<Map<String, Object>>> listRoles() {
        return rbacService.listRoles();
    }

    @PostMapping("/internal/rbac/roles")
    public Mono<Map<String, Object>> createRole(@RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.createRole(body, operator);
    }

    @PutMapping("/internal/rbac/roles/{roleCode}")
    public Mono<Map<String, Object>> updateRole(@PathVariable("roleCode") String roleCode,
                                                @RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.updateRole(roleCode, body, operator);
    }

    @DeleteMapping("/internal/rbac/roles/{roleCode}")
    public Mono<Map<String, Object>> deleteRole(@PathVariable("roleCode") String roleCode,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.deleteRole(roleCode, operator);
    }

    // ---------------- 权限矩阵 ----------------

    @GetMapping("/internal/rbac/permissions")
    public Mono<List<Map<String, Object>>> listPermissions() {
        return rbacService.listPermissions();
    }

    @GetMapping("/internal/rbac/perm-matrix")
    public Mono<List<Map<String, Object>>> getPermMatrix() {
        return rbacService.getPermMatrix();
    }

    @PostMapping("/internal/rbac/perm-matrix/cell")
    public Mono<Map<String, Object>> setPermCell(@RequestBody Map<String, Object> body,
                                                 @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.setPermCell(body, operator);
    }

    @PostMapping("/internal/rbac/perm-matrix/batch")
    public Mono<Map<String, Object>> setPermBatch(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.setPermBatch(body, operator);
    }

    // ---------------- 成员管理 ----------------

    @GetMapping("/internal/rbac/members")
    public Mono<List<Map<String, Object>>> listMembers() {
        return rbacService.listMembers();
    }

    @PostMapping("/internal/rbac/members/role")
    public Mono<Map<String, Object>> assignRole(@RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return rbacService.assignRole(body, operator);
    }
}

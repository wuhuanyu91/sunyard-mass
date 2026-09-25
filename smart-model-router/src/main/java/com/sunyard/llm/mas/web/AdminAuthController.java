package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.AdminAuthService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 管理端点令牌管理
 */
@RestController
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/internal/admin-auth/tokens")
    public Mono<Map<String, Object>> issue(@RequestBody(required = false) Map<String, Object> body,
                                           @RequestHeader(value = "X-Operator", required = false) String operator) {
        return adminAuthService.issue(body == null ? Map.of() : body, operator);
    }

    @GetMapping("/internal/admin-auth/tokens")
    public Mono<List<Map<String, Object>>> list() {
        return adminAuthService.list();
    }

    @DeleteMapping("/internal/admin-auth/tokens")
    public Mono<Map<String, Object>> revoke(@RequestBody Map<String, Object> body,
                                            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return adminAuthService.revoke(String.valueOf(body.get("token")), operator);
    }
}

package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.TenantService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 租户管理端点（公告二-1 多租户 / 二-2 租户隔离）
 */
@RestController
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping("/internal/tenants")
    public Mono<List<Map<String, Object>>> listTenants() {
        return tenantService.listTenants();
    }

    @PostMapping("/internal/tenants")
    public Mono<Map<String, Object>> createTenant(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        return tenantService.createTenant(body, operator);
    }

    @PutMapping("/internal/tenants/{tenantId}")
    public Mono<Map<String, Object>> updateTenant(@PathVariable("tenantId") String tenantId,
                                                  @RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        return tenantService.updateTenant(tenantId, body, operator);
    }

    /** 启用/停用：停用即收回模型与数据权限 */
    @PatchMapping("/internal/tenants/{tenantId}/status")
    public Mono<Map<String, Object>> setTenantStatus(@PathVariable("tenantId") String tenantId,
                                                     @RequestBody(required = false) Map<String, Object> body,
                                                     @RequestHeader(value = "X-Operator", required = false) String operator) {
        boolean enabled = body == null || !Boolean.FALSE.equals(body.get("enabled"));
        return tenantService.setTenantStatus(tenantId, enabled, operator);
    }

    @DeleteMapping("/internal/tenants/{tenantId}")
    public Mono<Map<String, Object>> deleteTenant(@PathVariable("tenantId") String tenantId,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        return tenantService.deleteTenant(tenantId, operator);
    }

    // ---------------- 映射配置化（消除硬编码 switch） ----------------

    @GetMapping("/internal/tenants/dept-mapping")
    public Mono<List<Map<String, Object>>> listDeptTenants() {
        return tenantService.listDeptTenants();
    }

    @PostMapping("/internal/tenants/dept-mapping")
    public Mono<Map<String, Object>> upsertDeptTenant(@RequestBody Map<String, Object> body,
                                                      @RequestHeader(value = "X-Operator", required = false) String operator) {
        return tenantService.upsertDeptTenant(body, operator);
    }

    @GetMapping("/internal/tenants/app-mapping")
    public Mono<List<Map<String, Object>>> listAppTenants() {
        return tenantService.listAppTenants();
    }

    @PostMapping("/internal/tenants/app-mapping")
    public Mono<Map<String, Object>> upsertAppTenant(@RequestBody Map<String, Object> body,
                                                     @RequestHeader(value = "X-Operator", required = false) String operator) {
        return tenantService.upsertAppTenant(body, operator);
    }
}

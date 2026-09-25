package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.PolicyService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 统一控制面策略端点（需求概览 5.2/六章）
 */
@RestController
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/internal/policies")
    public Mono<List<Map<String, Object>>> listPolicies(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) String status) {
        return policyService.listPolicies(category, status);
    }

    @PostMapping("/internal/policies")
    public Mono<Map<String, Object>> createPolicy(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        return policyService.createPolicy(body, operator);
    }

    @GetMapping("/internal/policies/{policyId}/versions")
    public Mono<List<Map<String, Object>>> listVersions(@PathVariable("policyId") String policyId) {
        return policyService.listVersions(policyId);
    }

    @PostMapping("/internal/policies/{policyId}/submit")
    public Mono<Map<String, Object>> submit(@PathVariable("policyId") String policyId,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return policyService.submit(policyId, body == null ? Map.of() : body, operator);
    }

    @PostMapping("/internal/policies/{policyId}/versions/{version}/approve")
    public Mono<Map<String, Object>> approve(@PathVariable("policyId") String policyId,
                                             @PathVariable("version") int version,
                                             @RequestBody(required = false) Map<String, Object> body,
                                             @RequestHeader(value = "X-Operator", required = false) String operator) {
        boolean approved = body == null || !Boolean.FALSE.equals(body.get("approved"));
        String comment = body == null ? null : (String) body.get("comment");
        return policyService.approve(policyId, version, approved, comment, operator);
    }

    @PostMapping("/internal/policies/{policyId}/versions/{version}/rollback")
    public Mono<Map<String, Object>> rollback(@PathVariable("policyId") String policyId,
                                              @PathVariable("version") int version,
                                              @RequestHeader(value = "X-Operator", required = false) String operator) {
        return policyService.rollback(policyId, version, operator);
    }

    /** 审批/回滚最新版本（前端策略中心工作台，不传 version） */
    @PostMapping("/internal/policies/{policyId}/approve")
    public Mono<Map<String, Object>> approveLatest(@PathVariable("policyId") String policyId,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   @RequestHeader(value = "X-Operator", required = false) String operator) {
        boolean approved = body == null || !Boolean.FALSE.equals(body.get("approved"));
        String comment = body == null ? null : (String) body.get("comment");
        return policyService.approveLatest(policyId, approved, comment, operator);
    }

    @PostMapping("/internal/policies/{policyId}/rollback")
    public Mono<Map<String, Object>> rollbackLatest(@PathVariable("policyId") String policyId,
                                                    @RequestHeader(value = "X-Operator", required = false) String operator) {
        return policyService.rollbackLatest(policyId, operator);
    }

    /** 单次请求执行了哪些策略 */
    @GetMapping("/internal/policies/exec-logs/{traceId}")
    public Mono<List<Map<String, Object>>> listExecLogs(@PathVariable("traceId") String traceId) {
        return policyService.listExecLogs(traceId);
    }
}

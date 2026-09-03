package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.SecurityService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 安全审计管理端点
 */
@RestController
public class SecurityController {

    private final SecurityService securityService;

    public SecurityController(SecurityService securityService) {
        this.securityService = securityService;
    }

    @GetMapping("/internal/security/events")
    public Mono<List<Map<String, Object>>> listSecurityEvents(
            @RequestParam(value = "event_type", required = false) String eventType,
            @RequestParam(value = "event_level", required = false) String eventLevel) {
        return securityService.listSecurityEvents(eventType, eventLevel);
    }

    @GetMapping("/internal/security/alerts")
    public Mono<List<Map<String, Object>>> listAlerts() {
        return securityService.listAlerts();
    }

    @GetMapping("/internal/security/guardrail")
    public Mono<Map<String, Object>> getGuardrailConfig() {
        return securityService.getGuardrailConfig();
    }

    @PutMapping("/internal/security/guardrail")
    public Mono<Map<String, Object>> saveGuardrailConfig(@RequestBody Map<String, Object> body) {
        return securityService.saveGuardrailConfig(body);
    }

    @GetMapping("/internal/security/guardrail/policies")
    public Mono<List<Map<String, Object>>> listGuardrailPolicies() {
        return securityService.listGuardrailPolicies();
    }

    @PostMapping("/internal/security/guardrail/policies")
    public Mono<Map<String, Object>> createGuardrailPolicy(@RequestBody Map<String, Object> body) {
        return securityService.createGuardrailPolicy(body);
    }

    @PutMapping("/internal/security/guardrail/policies/{policyId}")
    public Mono<Map<String, Object>> updateGuardrailPolicy(
            @PathVariable("policyId") String policyId,
            @RequestBody Map<String, Object> body) {
        return securityService.updateGuardrailPolicy(policyId, body);
    }

    @DeleteMapping("/internal/security/guardrail/policies/{policyId}")
    public Mono<Map<String, Object>> deleteGuardrailPolicy(@PathVariable("policyId") String policyId) {
        return securityService.deleteGuardrailPolicy(policyId);
    }
}

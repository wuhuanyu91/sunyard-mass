package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.SecurityEventService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 安全事件与告警端点（公告二-7 行为监测、异常识别）
 */
@RestController
public class SecurityEventController {

    private final SecurityEventService securityEventService;

    public SecurityEventController(SecurityEventService securityEventService) {
        this.securityEventService = securityEventService;
    }

    // ---------------- 检测规则 ----------------

    @GetMapping("/internal/security/detect-rules")
    public Mono<List<Map<String, Object>>> listRules() {
        return securityEventService.listRules();
    }

    @PostMapping("/internal/security/detect-rules")
    public Mono<Map<String, Object>> createRule(@RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return securityEventService.createRule(body, operator);
    }

    @PutMapping("/internal/security/detect-rules/{ruleCode}")
    public Mono<Map<String, Object>> updateRule(@PathVariable("ruleCode") String ruleCode,
                                                @RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return securityEventService.updateRule(ruleCode, body, operator);
    }

    @DeleteMapping("/internal/security/detect-rules/{ruleCode}")
    public Mono<Map<String, Object>> deleteRule(@PathVariable("ruleCode") String ruleCode,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return securityEventService.deleteRule(ruleCode, operator);
    }

    /** 手动触发一轮检测扫描 */
    @PostMapping("/internal/security/scan")
    public Mono<Map<String, Object>> scan(@RequestHeader(value = "X-Operator", required = false) String operator) {
        return securityEventService.scan(operator);
    }

    // ---------------- 事件 ----------------

    @GetMapping("/internal/security/events")
    public Mono<Map<String, Object>> listEvents(
            @RequestParam(value = "event_type", required = false) String eventType,
            @RequestParam(value = "event_level", required = false) String eventLevel,
            @RequestParam(value = "tenant_id", required = false) String tenantId,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "hours", defaultValue = "0") int hours,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return securityEventService.listEvents(eventType, eventLevel, tenantId, userId, hours, page, size);
    }

    // ---------------- 告警处置闭环 ----------------

    @GetMapping("/internal/security/alerts")
    public Mono<List<Map<String, Object>>> listAlerts() {
        return securityEventService.listAlerts();
    }

    /** 处置动作：ACKNOWLEDGED / HANDLING / CLOSED */
    @PostMapping("/internal/security/alerts/{alertId}/handle")
    public Mono<Map<String, Object>> handleAlert(@PathVariable("alertId") String alertId,
                                                 @RequestBody(required = false) Map<String, Object> body,
                                                 @RequestHeader(value = "X-Operator", required = false) String operator) {
        String status = body == null ? "ACKNOWLEDGED" : String.valueOf(body.getOrDefault("status", "ACKNOWLEDGED"));
        String comment = body == null ? null : (String) body.get("comment");
        return securityEventService.handleAlert(alertId, status, comment, operator);
    }
}

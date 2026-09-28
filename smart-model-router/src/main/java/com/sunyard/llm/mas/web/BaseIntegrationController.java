package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.BaseIntegrationService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 行内底座/运营管理体系对接端点（兼容适配#2：IAM / 4A / 统一监控 / 告警平台 / 工单系统）。
 * 外部行内系统对接为配置门控：未配置地址或启用前仅本地闭环；配置并启用后真实外呼。
 */
@RestController
@RequestMapping("/internal/integration")
public class BaseIntegrationController {

    private final BaseIntegrationService integrationService;

    public BaseIntegrationController(BaseIntegrationService integrationService) {
        this.integrationService = integrationService;
    }

    @GetMapping
    public Mono<List<Map<String, Object>>> list() {
        return integrationService.listIntegrations();
    }

    @PutMapping
    public Mono<Map<String, Object>> save(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return integrationService.saveIntegration(body);
    }

    @PostMapping("/{code}/test")
    public Mono<Map<String, Object>> test(
            @PathVariable("code") String code,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return integrationService.testConnectivity(code, operator);
    }

    @PostMapping("/iam/sync")
    public Mono<Map<String, Object>> syncIam(
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return integrationService.syncIam(operator);
    }

    @PostMapping("/monitor/push")
    public Mono<Map<String, Object>> pushMonitor(
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return integrationService.pushMonitor(operator);
    }

    @PostMapping("/alert/forward/{alertId}")
    public Mono<Map<String, Object>> forwardAlert(
            @PathVariable("alertId") String alertId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return integrationService.forwardAlert(alertId, operator);
    }

    @PostMapping("/ticket")
    public Mono<Map<String, Object>> createTicket(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return integrationService.createExternalTicket(body, operator);
    }

    @GetMapping("/logs")
    public Mono<List<Map<String, Object>>> logs() {
        return integrationService.getLogs();
    }
}

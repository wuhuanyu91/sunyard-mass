package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.RoutingService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 路由配置管理端点
 */
@RestController
public class RoutingController {

    private final RoutingService routingService;

    public RoutingController(RoutingService routingService) {
        this.routingService = routingService;
    }

    @GetMapping("/internal/routing/engine")
    public Mono<Map<String, Object>> getRoutingEngine() {
        return routingService.getRoutingEngine();
    }

    @PutMapping("/internal/routing/engine")
    public Mono<Map<String, Object>> saveRoutingEngine(@RequestBody Map<String, Object> body) {
        return routingService.saveRoutingEngine(body);
    }

    @GetMapping("/internal/routing/rate-limit-rules")
    public Mono<List<Map<String, Object>>> listRateLimitRules() {
        return routingService.listRateLimitRules();
    }

    @PostMapping("/internal/routing/rate-limit-rules")
    public Mono<Map<String, Object>> createRateLimitRule(@RequestBody Map<String, Object> body) {
        return routingService.createRateLimitRule(body);
    }

    @PutMapping("/internal/routing/rate-limit-rules/{ruleId}")
    public Mono<Map<String, Object>> updateRateLimitRule(
            @PathVariable("ruleId") String ruleId,
            @RequestBody Map<String, Object> body) {
        return routingService.updateRateLimitRule(ruleId, body);
    }

    @DeleteMapping("/internal/routing/rate-limit-rules/{ruleId}")
    public Mono<Map<String, Object>> deleteRateLimitRule(@PathVariable("ruleId") String ruleId) {
        return routingService.deleteRateLimitRule(ruleId);
    }

    @GetMapping("/internal/routing/routing-rule-sets")
    public Mono<List<Map<String, Object>>> listRoutingRuleSets() {
        return routingService.listRoutingRuleSets();
    }

    @PostMapping("/internal/routing/routing-rule-sets")
    public Mono<Map<String, Object>> saveRoutingRuleSet(@RequestBody Map<String, Object> body) {
        return routingService.saveRoutingRuleSet(body);
    }

    @GetMapping("/internal/routing/aggregation-groups")
    public Mono<List<Map<String, Object>>> listAggregationGroups() {
        return routingService.listAggregationGroups();
    }

    @PostMapping("/internal/routing/aggregation-groups")
    public Mono<Map<String, Object>> createAggregationGroup(@RequestBody Map<String, Object> body) {
        return routingService.createAggregationGroup(body);
    }

    @GetMapping("/internal/routing/elastic-switch")
    public Mono<Map<String, Object>> getElasticSwitch() {
        return routingService.getElasticSwitch();
    }

    @PutMapping("/internal/routing/elastic-switch")
    public Mono<Map<String, Object>> saveElasticSwitch(@RequestBody Map<String, Object> body) {
        return routingService.saveElasticSwitch(body);
    }

    @GetMapping("/internal/routing/router-logs")
    public Mono<List<Map<String, Object>>> listRouterLogs(
            @RequestParam(value = "trace_id", required = false) String traceId,
            @RequestParam(value = "app_id", required = false) String appId,
            @RequestParam(value = "status", required = false) String status) {
        return routingService.listRouterLogs(traceId, appId, status);
    }
}

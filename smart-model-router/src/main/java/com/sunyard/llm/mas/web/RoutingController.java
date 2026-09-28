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
    public Mono<Map<String, Object>> saveRoutingEngine(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.saveRoutingEngine(body, operator);
    }

    @GetMapping("/internal/routing/rate-limit-rules")
    public Mono<List<Map<String, Object>>> listRateLimitRules() {
        return routingService.listRateLimitRules();
    }

    @PostMapping("/internal/routing/rate-limit-rules")
    public Mono<Map<String, Object>> createRateLimitRule(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.createRateLimitRule(body, operator);
    }

    @PutMapping("/internal/routing/rate-limit-rules/{ruleId}")
    public Mono<Map<String, Object>> updateRateLimitRule(
            @PathVariable("ruleId") String ruleId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.updateRateLimitRule(ruleId, body, operator);
    }

    @DeleteMapping("/internal/routing/rate-limit-rules/{ruleId}")
    public Mono<Map<String, Object>> deleteRateLimitRule(
            @PathVariable("ruleId") String ruleId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.deleteRateLimitRule(ruleId, operator);
    }

    @GetMapping("/internal/routing/routing-rule-sets")
    public Mono<List<Map<String, Object>>> listRoutingRuleSets() {
        return routingService.listRoutingRuleSets();
    }

    @PostMapping("/internal/routing/routing-rule-sets")
    public Mono<Map<String, Object>> saveRoutingRuleSet(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.saveRoutingRuleSet(body, operator);
    }

    @DeleteMapping("/internal/routing/routing-rule-sets/{sceneKey}")
    public Mono<Map<String, Object>> deleteRoutingRuleSet(
            @PathVariable("sceneKey") String sceneKey,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.deleteRoutingRuleSet(sceneKey, operator);
    }

    @GetMapping("/internal/routing/aggregation-groups")
    public Mono<List<Map<String, Object>>> listAggregationGroups() {
        return routingService.listAggregationGroups();
    }

    @PostMapping("/internal/routing/aggregation-groups")
    public Mono<Map<String, Object>> createAggregationGroup(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.createAggregationGroup(body, operator);
    }

    @DeleteMapping("/internal/routing/aggregation-groups/{groupId}")
    public Mono<Map<String, Object>> deleteAggregationGroup(
            @PathVariable("groupId") String groupId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.deleteAggregationGroup(groupId, operator);
    }

    @GetMapping("/internal/routing/elastic-switch")
    public Mono<Map<String, Object>> getElasticSwitch() {
        return routingService.getElasticSwitch();
    }

    @PutMapping("/internal/routing/elastic-switch")
    public Mono<Map<String, Object>> saveElasticSwitch(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return routingService.saveElasticSwitch(body, operator);
    }

    @GetMapping("/internal/routing/router-logs")
    public Mono<List<Map<String, Object>>> listRouterLogs(
            @RequestParam(value = "trace_id", required = false) String traceId,
            @RequestParam(value = "app_id", required = false) String appId,
            @RequestParam(value = "status", required = false) String status) {
        return routingService.listRouterLogs(traceId, appId, status);
    }
}

package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Dashboard 管理端点：运营驾驶舱指标
 */
@RestController
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/internal/dashboard/summary")
    public Mono<Map<String, Object>> getSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/internal/dashboard/token-series")
    public Mono<List<Map<String, Object>>> getTokenSeries(
            @RequestParam(value = "hours", defaultValue = "24") Integer hours,
            @RequestParam(value = "step", defaultValue = "60") Integer step) {
        return dashboardService.getTokenSeries(hours, step);
    }

    @GetMapping("/internal/dashboard/trend-series")
    public Mono<List<Map<String, Object>>> getTrendSeries(
            @RequestParam(value = "hours", defaultValue = "24") Integer hours,
            @RequestParam(value = "step", defaultValue = "60") Integer step) {
        return dashboardService.getTrendSeries(hours, step);
    }

    @GetMapping("/internal/dashboard/dept-tco")
    public Mono<List<Map<String, Object>>> getDeptTco() {
        return dashboardService.getDeptTco();
    }

    @GetMapping("/internal/dashboard/app-tco-rank")
    public Mono<List<Map<String, Object>>> getAppTcoRank() {
        return dashboardService.getAppTcoRank();
    }

    @GetMapping("/internal/dashboard/model-tco-rank")
    public Mono<List<Map<String, Object>>> getModelTcoRank() {
        return dashboardService.getModelTcoRank();
    }

    @GetMapping("/internal/dashboard/funnel")
    public Mono<List<Map<String, Object>>> getFunnelData() {
        return dashboardService.getFunnelData();
    }

    @GetMapping("/internal/dashboard/rate-limit-hits")
    public Mono<List<Map<String, Object>>> getRateLimitHits() {
        return dashboardService.getRateLimitHits();
    }

    @GetMapping("/internal/dashboard/circuit-breakers")
    public Mono<List<Map<String, Object>>> getCircuitBreakers() {
        return dashboardService.getCircuitBreakers();
    }

    @GetMapping("/internal/dashboard/queue")
    public Mono<List<Map<String, Object>>> getQueueData() {
        return dashboardService.getQueueData();
    }

    @GetMapping("/internal/dashboard/batch-trend")
    public Mono<List<Map<String, Object>>> getBatchTrend() {
        return dashboardService.getBatchTrend();
    }

    @GetMapping("/internal/dashboard/heatmap")
    public Mono<List<Map<String, Object>>> getHeatmapData() {
        return dashboardService.getHeatmapData();
    }

    @GetMapping("/internal/dashboard/optimize-advice")
    public Mono<List<Map<String, Object>>> getOptimizeAdvice() {
        return dashboardService.getOptimizeAdvice();
    }
}

package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.MeteringService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 计量运营管理端点
 */
@RestController
public class MeteringController {

    private final MeteringService meteringService;

    public MeteringController(MeteringService meteringService) {
        this.meteringService = meteringService;
    }

    @GetMapping("/internal/metering/call-logs")
    public Mono<Map<String, Object>> listCallLogs(
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "app_id", required = false) String appId,
            @RequestParam(value = "model", required = false) String model,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return meteringService.listCallLogs(userId, appId, model, status, page, size);
    }

    @GetMapping("/internal/metering/model-stats")
    public Mono<List<Map<String, Object>>> getModelStats() {
        return meteringService.getModelStats();
    }

    @GetMapping("/internal/metering/quotas")
    public Mono<List<Map<String, Object>>> listQuotas() {
        return meteringService.listQuotas();
    }

    @PutMapping("/internal/metering/quotas/{deptId}")
    public Mono<Map<String, Object>> setQuota(
            @PathVariable("deptId") String deptId,
            @RequestBody Map<String, Object> body) {
        return meteringService.setQuota(deptId, body);
    }

    @GetMapping("/internal/metering/monthly-bills")
    public Mono<List<Map<String, Object>>> listMonthlyBills(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "dept_id", required = false) String deptId) {
        return meteringService.listMonthlyBills(month, deptId);
    }

    @GetMapping("/internal/metering/personal-usage")
    public Mono<Map<String, Object>> getPersonalUsage(
            @RequestParam(value = "user_id", required = false) String userId) {
        return meteringService.getPersonalUsage(userId);
    }

    @GetMapping("/internal/metering/model-recommends")
    public Mono<List<Map<String, Object>>> getModelRecommends() {
        return meteringService.getModelRecommends();
    }
}

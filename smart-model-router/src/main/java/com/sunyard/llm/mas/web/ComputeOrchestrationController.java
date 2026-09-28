package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.ComputeOrchestrationService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 弹性算力编排管理端点（此前前端 OrchestrationPanel / 错峰调度 / 异构厂商全为 mock）
 */
@RestController
public class ComputeOrchestrationController {

    private final ComputeOrchestrationService service;

    public ComputeOrchestrationController(ComputeOrchestrationService service) {
        this.service = service;
    }

    // ---------------- 编排配置 ----------------

    @GetMapping("/internal/compute/orchestration")
    public Mono<Map<String, Object>> getOrchestration() {
        return service.getOrchestration();
    }

    @PutMapping("/internal/compute/orchestration")
    public Mono<Map<String, Object>> saveOrchestration(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return service.saveOrchestration(body, operator);
    }

    // ---------------- 错峰调度 ----------------

    @GetMapping("/internal/compute/batch-tasks")
    public Mono<List<Map<String, Object>>> listBatchTasks(
            @RequestParam(value = "status", required = false) String status) {
        return service.listBatchTasks(status);
    }

    @PostMapping("/internal/compute/batch-tasks")
    public Mono<Map<String, Object>> createBatchTask(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return service.createBatchTask(body, operator);
    }

    @PostMapping("/internal/compute/batch-tasks/{taskId}/advance")
    public Mono<Map<String, Object>> advanceBatchTask(
            @PathVariable("taskId") String taskId,
            @RequestParam(value = "status", required = false) String status,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return service.advanceBatchTask(taskId, status, operator);
    }

    @DeleteMapping("/internal/compute/batch-tasks/{taskId}")
    public Mono<Map<String, Object>> cancelBatchTask(
            @PathVariable("taskId") String taskId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return service.cancelBatchTask(taskId, operator);
    }

    /** 错峰窗口建议（基于真实算力热区，无数据返回空） */
    @GetMapping("/internal/compute/off-peak-suggestions")
    public Mono<List<Map<String, Object>>> suggestOffPeakWindows() {
        return service.suggestOffPeakWindows();
    }

    // ---------------- 异构算力 ----------------

    @GetMapping("/internal/compute/vendors")
    public Mono<List<Map<String, Object>>> listHeteroVendors() {
        return service.listHeteroVendors();
    }

    @PostMapping("/internal/compute/vendors")
    public Mono<Map<String, Object>> saveHeteroVendor(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return service.saveHeteroVendor(body, operator);
    }

    @PostMapping("/internal/compute/vendors/discover")
    public Mono<Map<String, Object>> discoverVendors(
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return service.discoverVendors(operator);
    }
}

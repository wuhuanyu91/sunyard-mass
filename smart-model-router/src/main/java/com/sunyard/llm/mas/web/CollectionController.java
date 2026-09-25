package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.CollectionService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 分级采集与算力归集端点（公告一-1 分级采集机制 / 一-3 计算资源消耗归集）
 */
@RestController
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    // ---------------- 采集点 ----------------

    @GetMapping("/internal/collection/sources")
    public Mono<List<Map<String, Object>>> listSources() {
        return collectionService.listSources();
    }

    @PostMapping("/internal/collection/sources")
    public Mono<Map<String, Object>> registerSource(@RequestBody Map<String, Object> body,
                                                    @RequestHeader(value = "X-Operator", required = false) String operator) {
        return collectionService.registerSource(body, operator);
    }

    @PutMapping("/internal/collection/sources/{sourceCode}")
    public Mono<Map<String, Object>> updateSource(@PathVariable("sourceCode") String sourceCode,
                                                  @RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        return collectionService.updateSource(sourceCode, body, operator);
    }

    /** 渠道系统上报入口（带 push_token 校验） */
    @PostMapping("/internal/collection/ingest")
    public Mono<Map<String, Object>> ingest(@RequestHeader("X-Source-Code") String sourceCode,
                                            @RequestHeader("X-Push-Token") String pushToken,
                                            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) body.get("records");
        return collectionService.ingest(sourceCode, pushToken, records);
    }

    @GetMapping("/internal/collection/batches")
    public Mono<List<Map<String, Object>>> listBatches() {
        return collectionService.listBatches();
    }

    // ---------------- 算力 ----------------

    @PostMapping("/internal/compute/metrics")
    public Mono<Map<String, Object>> reportMetric(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        return collectionService.reportComputeMetric(body, operator);
    }

    @GetMapping("/internal/compute/nodes")
    public Mono<List<Map<String, Object>>> listCompute(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {
        return collectionService.listCompute(hours);
    }

    @GetMapping("/internal/compute/summary")
    public Mono<Map<String, Object>> computeSummary(
            @RequestParam(value = "hours", defaultValue = "24") int hours) {
        return collectionService.computeSummary(hours);
    }
}

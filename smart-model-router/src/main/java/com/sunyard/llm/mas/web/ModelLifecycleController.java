package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.ModelLifecycleService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 模型生命周期端点（版本 / 血缘 / 灰度发布与回滚）
 */
@RestController
public class ModelLifecycleController {

    private final ModelLifecycleService lifecycleService;

    public ModelLifecycleController(ModelLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/internal/models/{modelId}/versions")
    public Mono<List<Map<String, Object>>> listVersions(@PathVariable("modelId") String modelId) {
        return lifecycleService.listVersions(modelId);
    }

    @PostMapping("/internal/models/versions")
    public Mono<Map<String, Object>> createVersion(@RequestBody Map<String, Object> body,
                                                   @RequestHeader(value = "X-Operator", required = false) String operator) {
        return lifecycleService.createVersion(body, operator);
    }

    @PatchMapping("/internal/models/{modelId}/versions/{version}/status")
    public Mono<Map<String, Object>> setVersionStatus(@PathVariable("modelId") String modelId,
                                                      @PathVariable("version") String version,
                                                      @RequestBody Map<String, Object> body,
                                                      @RequestHeader(value = "X-Operator", required = false) String operator) {
        return lifecycleService.setVersionStatus(modelId, version, String.valueOf(body.get("status")), operator);
    }

    @GetMapping("/internal/models/{modelId}/lineage")
    public Mono<List<Map<String, Object>>> listLineage(@PathVariable("modelId") String modelId) {
        return lifecycleService.listLineage(modelId);
    }

    @PostMapping("/internal/models/lineage")
    public Mono<Map<String, Object>> addLineage(@RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return lifecycleService.addLineage(body, operator);
    }

    @GetMapping("/internal/models/releases")
    public Mono<List<Map<String, Object>>> listReleases() {
        return lifecycleService.listReleases();
    }

    @PostMapping("/internal/models/releases")
    public Mono<Map<String, Object>> startRelease(@RequestBody Map<String, Object> body,
                                                  @RequestHeader(value = "X-Operator", required = false) String operator) {
        return lifecycleService.startRelease(body, operator);
    }

    @PatchMapping("/internal/models/releases/{releaseId}/percent")
    public Mono<Map<String, Object>> adjustRelease(@PathVariable("releaseId") String releaseId,
                                                   @RequestBody Map<String, Object> body,
                                                   @RequestHeader(value = "X-Operator", required = false) String operator) {
        int percent = body.get("grayPercent") == null ? 10 : Integer.parseInt(String.valueOf(body.get("grayPercent")));
        return lifecycleService.adjustRelease(releaseId, percent, operator);
    }

    @PostMapping("/internal/models/releases/{releaseId}/rollback")
    public Mono<Map<String, Object>> rollback(@PathVariable("releaseId") String releaseId,
                                              @RequestHeader(value = "X-Operator", required = false) String operator) {
        return lifecycleService.rollbackRelease(releaseId, operator);
    }
}

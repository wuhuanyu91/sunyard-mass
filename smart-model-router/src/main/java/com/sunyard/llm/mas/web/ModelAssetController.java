package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.ModelAssetService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 模型资产管理端点
 */
@RestController
public class ModelAssetController {

    private final ModelAssetService modelAssetService;

    public ModelAssetController(ModelAssetService modelAssetService) {
        this.modelAssetService = modelAssetService;
    }

    @GetMapping("/internal/models")
    public Mono<List<Map<String, Object>>> listModelAssets() {
        return modelAssetService.listModelAssets();
    }

    @GetMapping("/internal/models/connections")
    public Mono<List<Map<String, Object>>> listModelConnections() {
        return modelAssetService.listModelConnections();
    }

    @PostMapping("/internal/models/connections")
    public Mono<Map<String, Object>> createModelConnection(@RequestBody Map<String, Object> body) {
        return modelAssetService.createModelConnection(body);
    }

    @PutMapping("/internal/models/connections/{connId}")
    public Mono<Map<String, Object>> updateModelConnection(
            @PathVariable("connId") String connId,
            @RequestBody Map<String, Object> body) {
        return modelAssetService.updateModelConnection(connId, body);
    }

    @DeleteMapping("/internal/models/connections/{connId}")
    public Mono<Map<String, Object>> deleteModelConnection(@PathVariable("connId") String connId) {
        return modelAssetService.deleteModelConnection(connId);
    }

    @PostMapping("/internal/models/connections/{connId}/test")
    public Mono<Map<String, Object>> testModelConnection(@PathVariable("connId") String connId) {
        return modelAssetService.testModelConnection(connId);
    }

    @GetMapping("/internal/models/evals")
    public Mono<List<Map<String, Object>>> listEvalResults(
            @RequestParam(value = "asset_id", required = false) String assetId) {
        return modelAssetService.listEvalResults(assetId);
    }

    @GetMapping("/internal/models/benefits")
    public Mono<List<Map<String, Object>>> listModelBenefits() {
        return modelAssetService.listModelBenefits();
    }
}

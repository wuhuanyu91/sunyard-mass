package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.DataLevelPolicyService;
import com.sunyard.llm.mas.service.ModelEvalArchiveService;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 模型评测 / 归档 / 下线依赖检查 + 数据分级管控策略管理端点
 */
@RestController
public class ModelEvalArchiveController {

    private final ModelEvalArchiveService evalArchiveService;
    private final DataLevelPolicyService dataLevelPolicyService;

    public ModelEvalArchiveController(ModelEvalArchiveService evalArchiveService,
                                      DataLevelPolicyService dataLevelPolicyService) {
        this.evalArchiveService = evalArchiveService;
        this.dataLevelPolicyService = dataLevelPolicyService;
    }

    // ---------------- 评测 ----------------

    /** 注意：/internal/models/evals 已被 ModelAssetController 占用（评测结果只读聚合），
     *  此处用 eval-records 承载可写的评测记录，避免 Spring 映射冲突。 */
    @GetMapping("/internal/models/eval-records")
    public Mono<List<Map<String, Object>>> listEvals(
            @RequestParam(value = "model_id", required = false) String modelId) {
        return evalArchiveService.listEvals(modelId);
    }

    @PostMapping("/internal/models/eval-records")
    public Mono<Map<String, Object>> saveEval(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return evalArchiveService.saveEval(body, operator);
    }

    // ---------------- 归档 ----------------

    @GetMapping("/internal/models/archives")
    public Mono<List<Map<String, Object>>> listArchives() {
        return evalArchiveService.listArchives();
    }

    @PostMapping("/internal/models/archives")
    public Mono<Map<String, Object>> archiveModel(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return evalArchiveService.archiveModel(body, operator);
    }

    @PostMapping("/internal/models/archives/{archiveId}/revive")
    public Mono<Map<String, Object>> reviveModel(
            @PathVariable("archiveId") String archiveId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return evalArchiveService.reviveModel(archiveId, operator);
    }

    @DeleteMapping("/internal/models/archives/{archiveId}")
    public Mono<Map<String, Object>> deleteArchive(
            @PathVariable("archiveId") String archiveId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return evalArchiveService.deleteArchive(archiveId, operator);
    }

    /** 按模型 ID 复活（前端归档列表以 assetId 为主键） */
    @PostMapping("/internal/models/archives/by-model/{modelId}/revive")
    public Mono<Map<String, Object>> reviveByModel(
            @PathVariable("modelId") String modelId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return evalArchiveService.reviveByModel(modelId, operator);
    }

    /** 按模型 ID 删除归档 */
    @DeleteMapping("/internal/models/archives/by-model/{modelId}")
    public Mono<Map<String, Object>> deleteByModel(
            @PathVariable("modelId") String modelId,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return evalArchiveService.deleteByModel(modelId, operator);
    }

    @GetMapping("/internal/models/archive-rules")
    public Mono<List<Map<String, Object>>> listArchiveRules() {
        return evalArchiveService.listArchiveRules();
    }

    @PutMapping("/internal/models/archive-rules")
    public Mono<Map<String, Object>> saveArchiveRules(
            @RequestBody Object body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return evalArchiveService.saveArchiveRules(body, operator);
    }

    @GetMapping("/internal/models/stale")
    public Mono<List<Map<String, Object>>> scanStaleModels(
            @RequestParam(value = "days", required = false, defaultValue = "90") int days) {
        return evalArchiveService.scanStaleModels(days);
    }

    // ---------------- 下线依赖检查 ----------------

    /** 下线前依赖检查：近 N 天仍在调用该模型的应用 */
    @GetMapping("/internal/models/{modelId}/dependencies")
    public Mono<Map<String, Object>> checkDependencies(
            @PathVariable("modelId") String modelId,
            @RequestParam(value = "days", required = false, defaultValue = "30") int days) {
        return evalArchiveService.checkDependencies(modelId, days);
    }

    // ---------------- 数据分级管控策略 ----------------

    @GetMapping("/internal/security/data-level-policies")
    public Mono<List<Map<String, Object>>> listDataLevelPolicies() {
        // listPolicies 内含同步查库（TTL 过期时 reload），必须经 ReactiveDbAdapter 切到弹性线程池，
        // 直接 Mono.fromCallable 会在 Netty 事件循环上阻塞
        return ReactiveDbAdapter.mono(dataLevelPolicyService::listPolicies).defaultIfEmpty(List.of());
    }

    @PutMapping("/internal/security/data-level-policies")
    public Mono<Map<String, Object>> saveDataLevelPolicy(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return ReactiveDbAdapter.mono(() -> dataLevelPolicyService.savePolicy(body, operator));
    }
}

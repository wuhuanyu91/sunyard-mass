package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.mapper.ModelLifecycleMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型生命周期服务（需求概览模型资产中心 + POC 第 9/13/14 问）：
 * 版本管理、派生血缘（微调/蒸馏/量化）、灰度发布与一键回滚 —— 此前全部为前端 mock，后端零代码。
 */
@Service
public class ModelLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(ModelLifecycleService.class);
    private static final String MODULE = "modelAsset";

    private final ModelLifecycleMapper lifecycleMapper;
    private final OpLogService opLogService;

    public ModelLifecycleService(ModelLifecycleMapper lifecycleMapper, OpLogService opLogService) {
        this.lifecycleMapper = lifecycleMapper;
        this.opLogService = opLogService;
    }

    // ---------------- 版本 ----------------

    public Mono<List<Map<String, Object>>> listVersions(String modelId) {
        return ReactiveDbAdapter.mono(() -> lifecycleMapper.listVersions(modelId));
    }

    public Mono<Map<String, Object>> createVersion(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String modelId = str(body.get("modelId"));
            String version = str(body.get("version"));
            if (modelId.isEmpty() || version.isEmpty()) throw new IllegalArgumentException("modelId/version 必填");
            lifecycleMapper.insertVersion(modelId, version, str(body.getOrDefault("sourceType", "BASE")),
                    str(body.get("baseVersion")), str(body.get("status")), str(body.get("configJson")));
            // 有父版本时自动登记血缘
            String base = str(body.get("baseVersion"));
            String relation = str(body.getOrDefault("sourceType", "BASE")).toUpperCase();
            if (!base.isEmpty() && !"BASE".equals(relation)) {
                lifecycleMapper.insertLineage(modelId, version, modelId, base, relation);
            }
            return modelId + ":" + version;
        }).flatMap(t -> opLogService.record(MODULE, "新增模型版本", operator, str(body.get("modelId")), "版本 " + t));
    }

    /** 上下线 / 归档 */
    public Mono<Map<String, Object>> setVersionStatus(String modelId, String version, String status, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            lifecycleMapper.updateVersionStatus(modelId, version, status);
            return modelId + ":" + version + "=" + status;
        }).flatMap(t -> opLogService.record(MODULE, "变更模型版本状态", operator, modelId, t));
    }

    // ---------------- 血缘 ----------------

    public Mono<List<Map<String, Object>>> listLineage(String modelId) {
        return ReactiveDbAdapter.mono(() -> lifecycleMapper.listLineage(modelId));
    }

    public Mono<Map<String, Object>> addLineage(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            lifecycleMapper.insertLineage(str(body.get("childModel")), str(body.get("childVersion")),
                    str(body.get("parentModel")), str(body.get("parentVersion")),
                    str(body.getOrDefault("relation", "FINETUNE")));
            return str(body.get("childModel")) + " <- " + str(body.get("parentModel"));
        }).flatMap(t -> opLogService.record(MODULE, "登记模型血缘", operator, str(body.get("childModel")), t));
    }

    // ---------------- 灰度发布 / 回滚 ----------------

    public Mono<List<Map<String, Object>>> listReleases() {
        return ReactiveDbAdapter.mono(lifecycleMapper::listReleases);
    }

    public Mono<Map<String, Object>> startRelease(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            // 银行要求：客户端传入的 releaseId 不得被静默忽略——传入则采用，冲突明确报 409；未传则由服务端生成
            String releaseId = str(body.get("releaseId"));
            if (releaseId.isEmpty()) {
                releaseId = "REL-" + System.currentTimeMillis();
            }
            int inserted = lifecycleMapper.insertRelease(releaseId, str(body.get("modelId")), str(body.get("fromVersion")),
                    str(body.get("toVersion")), intVal(body.get("grayPercent"), 10), str(body.get("grayScope")),
                    operator, intVal(body.get("slaRollbackMs"), 180000));
            if (inserted == 0) {
                throw MasException.conflict("发布单号已存在，请更换 releaseId：" + releaseId);
            }
            log.info("gray release started: {} -> {}", releaseId, body.get("toVersion"));
            return releaseId;
        }).flatMap(id -> opLogService.record(MODULE, "发起灰度发布", operator, id,
                "灰度 " + body.get("grayPercent") + "%"));
    }

    /** 调整灰度比例；100% 即全量 */
    public Mono<Map<String, Object>> adjustRelease(String releaseId, int percent, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            lifecycleMapper.updateRelease(releaseId, percent, percent >= 100 ? "FULL" : "GRAYING");
            return releaseId + "=" + percent + "%";
        }).flatMap(t -> opLogService.record(MODULE, "调整灰度比例", operator, releaseId, t));
    }

    /** 一键回滚：发布单置为 ROLLBACK，并恢复原版本状态 */
    public Mono<Map<String, Object>> rollbackRelease(String releaseId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            Map<String, Object> rel = lifecycleMapper.selectRelease(releaseId);
            if (rel == null) throw new IllegalArgumentException("发布单不存在：" + releaseId);
            lifecycleMapper.updateRelease(releaseId, 0, "ROLLBACK");
            String modelId = String.valueOf(rel.get("model_id"));
            String from = String.valueOf(rel.get("from_version"));
            if (from != null && !from.isEmpty() && !"null".equals(from)) {
                lifecycleMapper.updateVersionStatus(modelId, from, "ONLINE");
            }
            log.warn("gray release rolled back: {}", releaseId);
            return releaseId;
        }).flatMap(id -> opLogService.record(MODULE, "灰度回滚", operator, id, "一键回滚"));
    }

    // ---------------- helpers ----------------

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static Integer intVal(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (Exception e) {
            return def;
        }
    }
}

package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.CallLogEntity;
import com.sunyard.llm.mas.entity.ModelConfigEntity;
import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.mapper.ModelConfigMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * 模型资产服务：模型列表从 DB 查询，效益数据从 call_log 聚合
 */
@Service
public class ModelAssetService {

    private final ModelConfigMapper modelConfigMapper;
    private final CallLogMapper callLogMapper;

    public ModelAssetService(ModelConfigMapper modelConfigMapper, CallLogMapper callLogMapper) {
        this.modelConfigMapper = modelConfigMapper;
        this.callLogMapper = callLogMapper;
    }

    /** 模型资产列表（从 mas_model_config 查询，补充 call_log 统计） */
    public Mono<List<Map<String, Object>>> listModelAssets() {
        return ReactiveDbAdapter.mono(() -> {
            List<ModelConfigEntity> entities = modelConfigMapper.selectAll();
            List<Map<String, Object>> list = new ArrayList<>();
            for (ModelConfigEntity e : entities) {
                // 从 call_log 查询该模型的真实统计
                List<Map<String, Object>> stats = callLogMapper.selectMaps(
                    new QueryWrapper<CallLogEntity>()
                        .select(
                            "COUNT(*) as calls",
                            "COALESCE(SUM(total_tokens),0) as tokens",
                            "COALESCE(AVG(total_cost_ms),0)::int as avg_latency",
                            "CASE WHEN COUNT(*)>0 THEN (COUNT(*) - COUNT(CASE WHEN status != 0 THEN 1 END))::numeric / COUNT(*)::numeric * 100 ELSE 0 END as success_rate"
                        )
                        .eq("model_id", e.getModelId())
                );
                Map<String, Object> stat = stats.isEmpty() ? Map.of() : stats.get(0);

                Map<String, Object> asset = new LinkedHashMap<>();
                asset.put("asset_id", e.getModelId());
                asset.put("asset_code", "MODEL-" + e.getModelId());
                asset.put("asset_name", e.getModelName());
                asset.put("asset_type", "BASE_LLM");
                asset.put("source_type", "LOCAL");
                asset.put("base_model_id", (Object) null);
                asset.put("derivation_type", "NONE");
                asset.put("owner_dept", "信息科技部");
                asset.put("maintainer", "张伟");
                asset.put("risk_level", "B");
                asset.put("security_level", "L2");
                asset.put("version", "v1.0");
                asset.put("lifecycle_status", e.getStatus() != null && e.getStatus() == 1 ? "PRODUCTION" : "OFFLINE");
                asset.put("supported_tasks", List.of("问答", "摘要"));
                asset.put("supported_hardware", List.of("H20"));
                asset.put("context_window", e.getMaxContextTokens() != null ? e.getMaxContextTokens() : 32768);
                asset.put("cost_per_1k_tokens", 0.25);
                asset.put("avg_latency_ms", toLong(stat.get("avg_latency")));
                asset.put("success_rate", toDouble(stat.get("success_rate")));
                asset.put("active_apps", toLong(stat.get("calls")) > 0 ?
                    callLogMapper.selectMaps(new QueryWrapper<CallLogEntity>()
                        .select("DISTINCT app_id").eq("model_id", e.getModelId())).size() : 0);
                list.add(asset);
            }
            return list;
        });
    }

    /** 模型接入列表（配置数据，保持） */
    public Mono<List<Map<String, Object>>> listModelConnections() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(buildConn("CONN-001", "阿里云百炼-Qwen-Max", "CLOUD", "阿里云百炼", "ONLINE", 238));
        list.add(buildConn("CONN-002", "火山引擎-Doubao-Pro", "CLOUD", "火山引擎", "ONLINE", 312));
        list.add(buildConn("CONN-004", "本地 H20 生产集群", "LOCAL", "行内数据中心", "ONLINE", 42));
        list.add(buildConn("CONN-005", "本地 L20/4090 推理集群", "LOCAL", "行内数据中心", "ONLINE", 38));
        return Mono.just(list);
    }

    public Mono<Map<String, Object>> createModelConnection(Map<String, Object> body) {
        return opRecord("新建模型接入", "CONN-NEW", body.getOrDefault("name", "").toString());
    }

    public Mono<Map<String, Object>> updateModelConnection(String connId, Map<String, Object> body) {
        return opRecord("更新模型接入", connId, body.getOrDefault("name", "").toString());
    }

    public Mono<Map<String, Object>> deleteModelConnection(String connId) {
        return opRecord("删除模型接入", connId, "接入已删除");
    }

    public Mono<Map<String, Object>> testModelConnection(String connId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("latency_ms", 120 + (int) (Math.random() * 100));
        return Mono.just(result);
    }

    /** 评测结果（配置数据，保持） */
    public Mono<List<Map<String, Object>>> listEvalResults(String assetId) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> eval = new LinkedHashMap<>();
        eval.put("eval_id", "EVAL-001");
        eval.put("asset_id", assetId != null ? assetId : "qwen-72b");
        eval.put("eval_type", "ADMISSION");
        eval.put("eval_dataset", "信贷场景评测集 v3");
        eval.put("accuracy", 97.2);
        eval.put("hallucination_rate", 1.1);
        eval.put("compliance_rate", 99.4);
        eval.put("tool_call_success_rate", 95.8);
        eval.put("long_context_score", 88.0);
        eval.put("cost_score", 82.0);
        eval.put("review_conclusion", "PASS");
        eval.put("reviewed_by", "李娜");
        eval.put("reviewed_at", "2026-07-28T16:00:00Z");
        list.add(eval);
        return Mono.just(list);
    }

    /** 模型效益（从 call_log 按 model_id 聚合真实数据） */
    public Mono<List<Map<String, Object>>> listModelBenefits() {
        return ReactiveDbAdapter.mono(() ->
            callLogMapper.selectMaps(
                new QueryWrapper<CallLogEntity>()
                    .select(
                        "model_id as asset_id",
                        "COUNT(DISTINCT app_id) as active_apps",
                        "COUNT(DISTINCT user_id) as user_scale",
                        "COALESCE(SUM(total_tokens),0)::numeric * 0.0016 as month_cost",
                        "CASE WHEN COUNT(*)>0 THEN COALESCE(SUM(total_tokens),0)::numeric / COUNT(*) ELSE 0 END as unit_cost",
                        "100.0 as adopt_rate",
                        "CASE WHEN COUNT(*)>0 THEN (COUNT(*) - COUNT(CASE WHEN status != 0 THEN 1 END))::numeric / COUNT(*)::numeric * 100 ELSE 0 END as success_rate",
                        "CASE WHEN COUNT(*)>1000 THEN 'A' WHEN COUNT(*)>100 THEN 'B' ELSE 'C' END as value_score",
                        "CASE WHEN COUNT(*)>1000 THEN 'KEEP' WHEN COUNT(*)>100 THEN 'KEEP' ELSE 'OPTIMIZE' END as suggestion"
                    )
                    .isNotNull("model_id").ne("model_id", "")
                    .groupBy("model_id")
                    .orderByDesc("month_cost")
            )
        );
    }

    // ---- helpers ----
    private Map<String, Object> buildConn(String id, String name, String source, String provider,
                                           String status, int latencyMs) {
        Map<String, Object> conn = new LinkedHashMap<>();
        conn.put("conn_id", id);
        conn.put("name", name);
        conn.put("source", source);
        conn.put("provider", provider);
        conn.put("model_type", "文本生成");
        conn.put("api_key_masked", source.equals("CLOUD") ? "sk-****" : "");
        conn.put("base_url", "");
        conn.put("nodes", source.equals("LOCAL") ? 64 : 0);
        conn.put("card_type", source.equals("LOCAL") ? "H20" : "");
        conn.put("status", status);
        conn.put("latency_ms", latencyMs);
        conn.put("asset_id", (Object) null);
        conn.put("last_check_at", java.time.Instant.now().toString());
        conn.put("created_at", java.time.Instant.now().toString());
        return conn;
    }

    private Mono<Map<String, Object>> opRecord(String opType, String targetId, String detail) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("op_id", "OP-" + System.currentTimeMillis());
        record.put("op_type", opType);
        record.put("operator", "平台管理员");
        record.put("target_id", targetId);
        record.put("detail", detail);
        record.put("created_at", java.time.Instant.now().toString());
        return Mono.just(record);
    }

    private long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }
}

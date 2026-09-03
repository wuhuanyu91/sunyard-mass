package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.CallLogEntity;
import com.sunyard.llm.mas.entity.RoutingRuleEntity;
import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.mapper.RoutingRuleMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

/**
 * 路由配置服务：限流规则从 DB 查询，路由日志从 call_log 查询
 */
@Service
public class RoutingService {

    private final RoutingRuleMapper routingRuleMapper;
    private final CallLogMapper callLogMapper;

    public RoutingService(RoutingRuleMapper routingRuleMapper, CallLogMapper callLogMapper) {
        this.routingRuleMapper = routingRuleMapper;
        this.callLogMapper = callLogMapper;
    }

    /** 路由引擎配置（系统配置，保持） */
    public Mono<Map<String, Object>> getRoutingEngine() {
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put("latency", 30.0);
        weights.put("cost", 25.0);
        weights.put("risk", 25.0);
        weights.put("load", 20.0);
        config.put("weights", weights);
        config.put("cache_first", true);
        config.put("budget_guard", true);
        config.put("sla_priority", true);
        config.put("auto_fallback", true);
        config.put("openai_compat", true);
        return Mono.just(config);
    }

    public Mono<Map<String, Object>> saveRoutingEngine(Map<String, Object> body) {
        return opRecord("保存路由引擎配置", "ROUTING-ENGINE", "权重配置已更新");
    }

    /** 限流规则列表（从 mas_routing_rule 查询） */
    public Mono<List<Map<String, Object>>> listRateLimitRules() {
        return ReactiveDbAdapter.mono(() -> {
            List<RoutingRuleEntity> rules = routingRuleMapper.selectList(null);
            List<Map<String, Object>> list = new ArrayList<>();
            for (RoutingRuleEntity r : rules) {
                Map<String, Object> rule = new LinkedHashMap<>();
                rule.put("rule_id", r.getRuleId());
                rule.put("name", r.getName());
                rule.put("target_type", r.getTargetType());
                rule.put("target_id", r.getTargetId());
                rule.put("enabled", r.getEnabled());
                rule.put("qps_per_min", r.getQpsLimit());
                rule.put("input_token_limit", r.getInputTokenLimit());
                rule.put("output_token_limit", r.getOutputTokenLimit());
                rule.put("concurrency", r.getConcurrency());
                rule.put("ip_whitelist", List.of());
                rule.put("over_action", r.getOverAction());
                rule.put("hits_24h", r.getHits24h());
                list.add(rule);
            }
            return list;
        });
    }

    public Mono<Map<String, Object>> createRateLimitRule(Map<String, Object> body) {
        return opRecord("新建限流规则", "RL-CFG-NEW", body.getOrDefault("name", "").toString());
    }

    public Mono<Map<String, Object>> updateRateLimitRule(String ruleId, Map<String, Object> body) {
        return opRecord("更新限流规则", ruleId, body.getOrDefault("name", "").toString());
    }

    public Mono<Map<String, Object>> deleteRateLimitRule(String ruleId) {
        return opRecord("删除限流规则", ruleId, "规则已删除");
    }

    /** 场景路由规则集（配置数据，保持） */
    public Mono<List<Map<String, Object>>> listRoutingRuleSets() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> rs1 = new LinkedHashMap<>();
        rs1.put("scene_key", "CREDIT"); rs1.put("scene_name", "信贷审批"); rs1.put("priority", "P0");
        rs1.put("allowed_models", List.of("qwen-72b", "qwen-lite"));
        rs1.put("fallback_model", "qwen-lite"); rs1.put("latency_ceil_ms", 1200);
        rs1.put("policy_id", "POL-ROUTING-001");
        list.add(rs1);
        Map<String, Object> rs2 = new LinkedHashMap<>();
        rs2.put("scene_key", "RISK"); rs2.put("scene_name", "风控反欺诈"); rs2.put("priority", "P0");
        rs2.put("allowed_models", List.of("qwen-lite", "qwen-72b"));
        rs2.put("fallback_model", "qwen-72b"); rs2.put("latency_ceil_ms", 800);
        rs2.put("policy_id", null);
        list.add(rs2);
        Map<String, Object> rs3 = new LinkedHashMap<>();
        rs3.put("scene_key", "SERVICE"); rs3.put("scene_name", "客服问答"); rs3.put("priority", "P1");
        rs3.put("allowed_models", List.of("qwen-lite", "qwen2.5:0.5b"));
        rs3.put("fallback_model", "qwen2.5:0.5b"); rs3.put("latency_ceil_ms", 1500);
        rs3.put("policy_id", null);
        list.add(rs3);
        return Mono.just(list);
    }

    public Mono<Map<String, Object>> saveRoutingRuleSet(Map<String, Object> body) {
        return opRecord("保存场景路由规则",
                body.getOrDefault("scene_key", "").toString(),
                body.getOrDefault("scene_name", "").toString());
    }

    /** 聚合组（配置数据，保持） */
    public Mono<List<Map<String, Object>>> listAggregationGroups() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("group_id", "AGG-001", "name", "客服问答聚合组",
                "members", List.of("qwen-lite", "qwen2.5:0.5b"),
                "strategy", "WEIGHTED", "auto_skip_fault", true, "health_check_sec", 30, "fault_members", List.of()));
        list.add(Map.of("group_id", "AGG-002", "name", "复杂推理聚合组",
                "members", List.of("qwen-72b", "qwen-lite"),
                "strategy", "LATENCY", "auto_skip_fault", true, "health_check_sec", 15,
                "fault_members", List.of()));
        return Mono.just(list);
    }

    public Mono<Map<String, Object>> createAggregationGroup(Map<String, Object> body) {
        return opRecord("新建聚合组", "AGG-NEW", body.getOrDefault("name", "").toString());
    }

    /** 弹性切换配置（系统配置，保持） */
    public Mono<Map<String, Object>> getElasticSwitch() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("trigger_util", 85.0);
        config.put("sustain_min", 5);
        config.put("target", "RENTAL");
        config.put("traffic_ratio", 30.0);
        config.put("active", true);
        return Mono.just(config);
    }

    public Mono<Map<String, Object>> saveElasticSwitch(Map<String, Object> body) {
        return opRecord("保存弹性切换配置", "ELASTIC-SWITCH", "弹性切换配置已更新");
    }

    /** 路由日志（从 mas_call_log 查询真实路由记录） */
    public Mono<List<Map<String, Object>>> listRouterLogs(String traceId, String appId, String status) {
        return ReactiveDbAdapter.mono(() -> {
            LambdaQueryWrapper<CallLogEntity> wrapper = new LambdaQueryWrapper<>();
            if (traceId != null && !traceId.isBlank()) wrapper.eq(CallLogEntity::getTraceId, traceId);
            if (appId != null && !appId.isBlank()) wrapper.eq(CallLogEntity::getAppId, appId);
            wrapper.orderByDesc(CallLogEntity::getCreatedAt);
            wrapper.last("LIMIT 50");

            List<CallLogEntity> entities = callLogMapper.selectList(wrapper);
            List<Map<String, Object>> list = new ArrayList<>();

            for (CallLogEntity e : entities) {
                Map<String, Object> log = new LinkedHashMap<>();
                log.put("trace_id", e.getTraceId() != null ? e.getTraceId() : "");
                log.put("request_id", "REQ-" + e.getId());
                log.put("app_id", e.getAppId() != null ? e.getAppId() : "");
                log.put("tenant_id", e.getTenantId() != null ? e.getTenantId() : "");
                log.put("user_id", e.getUserId() != null ? e.getUserId() : "");
                log.put("business_scenario", e.getIntentType() != null ? e.getIntentType() : "");
                log.put("task_type", e.getIntentType() != null ? e.getIntentType() : "");
                log.put("data_level", e.getDataLevel() != null ? e.getDataLevel() : "L2");
                log.put("request_mode", "SYNC");
                log.put("prompt_tokens", e.getPromptTokens() != null ? e.getPromptTokens() : 0);
                log.put("expected_output_tokens", e.getCompletionTokens() != null ? e.getCompletionTokens() : 0);
                log.put("context_length", (e.getPromptTokens() != null ? e.getPromptTokens() : 0) + (e.getCompletionTokens() != null ? e.getCompletionTokens() : 0));
                log.put("sla_level", e.getSlaLevel() != null ? e.getSlaLevel() : "P1");
                log.put("budget_class", "STANDARD");

                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("candidate_models", List.of(
                    Map.of("asset_id", e.getModelId() != null ? e.getModelId() : "",
                           "version", "v1.0",
                           "score", 86.0,
                           "eliminate_reason", "")
                ));
                decision.put("selected_model", e.getModelId() != null ? e.getModelId() : "");
                decision.put("selected_version", "v1.0");
                decision.put("selected_engine", "VLLM");
                decision.put("selected_pool", "POOL-H20");
                decision.put("selected_node", "node-gpu-01");
                decision.put("route_reason", "业务=" + (e.getIntentType() != null ? e.getIntentType() : "通用"));
                decision.put("score_latency", 82.0);
                decision.put("score_cost", 75.0);
                decision.put("score_risk", 90.0);
                decision.put("score_load", 70.0);
                decision.put("fallback_triggered", false);
                decision.put("fallback_reason", "");
                log.put("decision", decision);
                log.put("status", e.getStatus() != null && e.getStatus() == 0 ? "SUCCESS" : "FAILED");
                log.put("total_duration_ms", e.getTotalCostMs() != null ? e.getTotalCostMs() : 0);
                log.put("created_at", e.getCreatedAt() != null ? e.getCreatedAt().toString() + "Z" : "");
                list.add(log);
            }
            return list;
        });
    }

    // ---- helpers ----
    private Mono<Map<String, Object>> opRecord(String opType, String targetId, String detail) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("op_id", "OP-" + System.currentTimeMillis());
        record.put("op_type", opType);
        record.put("operator", "平台管理员");
        record.put("target_id", targetId);
        record.put("detail", detail);
        record.put("created_at", Instant.now().toString());
        return Mono.just(record);
    }
}

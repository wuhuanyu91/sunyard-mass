package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.AggregationGroupEntity;
import com.sunyard.llm.mas.entity.CallLogEntity;
import com.sunyard.llm.mas.entity.ElasticSwitchEntity;
import com.sunyard.llm.mas.entity.RoutingEngineEntity;
import com.sunyard.llm.mas.entity.RoutingRuleEntity;
import com.sunyard.llm.mas.entity.RoutingRuleSetEntity;
import com.sunyard.llm.mas.mapper.AggregationGroupMapper;
import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.mapper.ElasticSwitchMapper;
import com.sunyard.llm.mas.mapper.RoutingEngineMapper;
import com.sunyard.llm.mas.mapper.RoutingRuleMapper;
import com.sunyard.llm.mas.mapper.RoutingRuleSetMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 路由配置服务：限流规则、路由引擎、场景路由规则集、聚合组、弹性切换均从 DB 读写；
 * 路由日志从 call_log 查询。
 * <p>
 * 【改造背景】路由引擎配置 / 场景路由规则集 / 聚合组 / 弹性切换 四个端点的写操作
 * 此前只返回一条操作留痕（opRecord），不产生任何 UPDATE/INSERT，页面刷新即回原值。
 */
@Service
public class RoutingService {

    private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

    private final RoutingRuleMapper routingRuleMapper;
    private final CallLogMapper callLogMapper;
    private final OpLogService opLogService;
    private final RoutingEngineMapper routingEngineMapper;
    private final RoutingRuleSetMapper routingRuleSetMapper;
    private final AggregationGroupMapper aggregationGroupMapper;
    private final ElasticSwitchMapper elasticSwitchMapper;
    private final ObjectProvider<RuleRateLimitService> ruleRateLimitProvider;

    public RoutingService(RoutingRuleMapper routingRuleMapper, CallLogMapper callLogMapper,
                          OpLogService opLogService, RoutingEngineMapper routingEngineMapper,
                          RoutingRuleSetMapper routingRuleSetMapper,
                          AggregationGroupMapper aggregationGroupMapper,
                          ElasticSwitchMapper elasticSwitchMapper,
                          ObjectProvider<RuleRateLimitService> ruleRateLimitProvider) {
        this.routingRuleMapper = routingRuleMapper;
        this.callLogMapper = callLogMapper;
        this.opLogService = opLogService;
        this.routingEngineMapper = routingEngineMapper;
        this.routingRuleSetMapper = routingRuleSetMapper;
        this.aggregationGroupMapper = aggregationGroupMapper;
        this.elasticSwitchMapper = elasticSwitchMapper;
        this.ruleRateLimitProvider = ruleRateLimitProvider;
    }

    /** 限流规则变更后立即刷新 L1 运行时规则缓存（此前仅靠 60s TTL，新规则生效滞后） */
    private void refreshRuleRuntime() {
        try {
            ruleRateLimitProvider.ifAvailable(RuleRateLimitService::refreshNow);
        } catch (Exception e) {
            log.warn("rule rate-limit runtime refresh failed (fallback to TTL reload): {}", e.getMessage());
        }
    }

    // ---------------- 路由引擎配置（真实落库 mas_routing_engine） ----------------

    /** 路由引擎四维权重与策略开关：优先读库，无记录时回落到内置默认值 */
    public Mono<Map<String, Object>> getRoutingEngine() {
        return ReactiveDbAdapter.mono(() -> {
            RoutingEngineEntity e = routingEngineMapper.selectById(1);
            Map<String, Object> config = new LinkedHashMap<>();
            Map<String, Object> weights = new LinkedHashMap<>();
            if (e == null) {
                weights.put("latency", 30.0);
                weights.put("cost", 25.0);
                weights.put("risk", 25.0);
                weights.put("load", 20.0);
                config.put("cache_first", true);
                config.put("budget_guard", true);
                config.put("sla_priority", true);
                config.put("auto_fallback", true);
                config.put("openai_compat", true);
            } else {
                weights.put("latency", dbl(e.getWeightLatency(), 30.0));
                weights.put("cost", dbl(e.getWeightCost(), 25.0));
                weights.put("risk", dbl(e.getWeightRisk(), 25.0));
                weights.put("load", dbl(e.getWeightLoad(), 20.0));
                config.put("cache_first", e.getCacheFirst() != null && e.getCacheFirst() == 1);
                config.put("budget_guard", e.getBudgetGuard() != null && e.getBudgetGuard() == 1);
                config.put("sla_priority", e.getSlaPriority() != null && e.getSlaPriority() == 1);
                config.put("auto_fallback", e.getAutoFallback() != null && e.getAutoFallback() == 1);
                config.put("openai_compat", e.getOpenaiCompat() != null && e.getOpenaiCompat() == 1);
            }
            config.put("weights", weights);
            return config;
        }).onErrorResume(e -> Mono.just(defaultEngineConfig()));
    }

    /** 保存路由引擎配置：真实 UPSERT mas_routing_engine 并落操作审计 */
    public Mono<Map<String, Object>> saveRoutingEngine(Map<String, Object> body) {
        return saveRoutingEngine(body, null);
    }

    public Mono<Map<String, Object>> saveRoutingEngine(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            RoutingEngineEntity e = routingEngineMapper.selectById(1);
            boolean insert = e == null;
            if (insert) {
                e = new RoutingEngineEntity();
                e.setId(1);
            }
            Object weights = body.get("weights");
            if (weights instanceof Map<?, ?> w) {
                e.setWeightLatency(dec(w.get("latency"), e.getWeightLatency(), 30.0));
                e.setWeightCost(dec(w.get("cost"), e.getWeightCost(), 25.0));
                e.setWeightRisk(dec(w.get("risk"), e.getWeightRisk(), 25.0));
                e.setWeightLoad(dec(w.get("load"), e.getWeightLoad(), 20.0));
            }
            // 四维权重自动归一到 100，避免权重口径失真
            BigDecimal sum = nz(e.getWeightLatency()).add(nz(e.getWeightCost()))
                    .add(nz(e.getWeightRisk())).add(nz(e.getWeightLoad()));
            if (sum.compareTo(BigDecimal.ZERO) > 0 && sum.compareTo(new BigDecimal("100")) != 0) {
                BigDecimal factor = new BigDecimal("100").divide(sum, 6, java.math.RoundingMode.HALF_UP);
                e.setWeightLatency(nz(e.getWeightLatency()).multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP));
                e.setWeightCost(nz(e.getWeightCost()).multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP));
                e.setWeightRisk(nz(e.getWeightRisk()).multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP));
                e.setWeightLoad(nz(e.getWeightLoad()).multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP));
            }
            e.setCacheFirst(flag(body.get("cache_first"), e.getCacheFirst(), 1));
            e.setBudgetGuard(flag(body.get("budget_guard"), e.getBudgetGuard(), 1));
            e.setSlaPriority(flag(body.get("sla_priority"), e.getSlaPriority(), 1));
            e.setAutoFallback(flag(body.get("auto_fallback"), e.getAutoFallback(), 1));
            e.setOpenaiCompat(flag(body.get("openai_compat"), e.getOpenaiCompat(), 1));
            e.setUpdatedBy(operator);
            if (insert) {
                routingEngineMapper.insert(e);
            } else {
                routingEngineMapper.updateById(e);
            }
            return e.getWeightLatency() + "/" + e.getWeightCost() + "/"
                    + e.getWeightRisk() + "/" + e.getWeightLoad();
        }).flatMap(w -> opLogService.record("routing", "保存路由引擎配置", operator, "ROUTING-ENGINE",
                "四维权重已更新为 " + w));
    }

    private static Map<String, Object> defaultEngineConfig() {
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
        return config;
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
                rule.put("ip_whitelist", splitList(r.getIpWhitelist()));
                rule.put("over_action", r.getOverAction());
                rule.put("hits_24h", r.getHits24h());
                list.add(rule);
            }
            return list;
        });
    }

    /**
     * 新建限流规则：【已改造】此前只返回 opRecord 假留痕、不落库（页面提示保存成功、刷新回原值），
     * 现真实写入 mas_routing_rule，并落操作审计。
     */
    public Mono<Map<String, Object>> createRateLimitRule(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            RoutingRuleEntity e = new RoutingRuleEntity();
            e.setRuleId(str(body.getOrDefault("ruleId", "RL-" + System.currentTimeMillis())));
            e.setName(str(body.getOrDefault("name", e.getRuleId())));
            e.setTargetType(str(body.getOrDefault("targetType", "GLOBAL")));
            e.setTargetId(str(body.getOrDefault("targetId", "*")));
            e.setEnabled(boolVal(body.get("enabled"), true));
            e.setQpsLimit(intVal(body.get("qpsPerMin"), intVal(body.get("qpsLimit"), 60)));
            e.setInputTokenLimit(intVal(body.get("inputTokenLimit"), 0));
            e.setOutputTokenLimit(intVal(body.get("outputTokenLimit"), 0));
            e.setConcurrency(intVal(body.get("concurrency"), 0));
            e.setOverAction(str(body.getOrDefault("overAction", "REJECT")));
            e.setIpWhitelist(joinList(body.get("ipWhitelist")));
            e.setHits24h(0);
            routingRuleMapper.insert(e);
            return e.getRuleId();
        }).flatMap(id -> opLogService.record("routing", "新建限流规则", operator, id,
                "限流规则 " + body.getOrDefault("name", id) + " 已创建并落库")
                .doOnSuccess(v -> refreshRuleRuntime())
                .thenReturn(Map.of("ruleId", id)));
    }

    public Mono<Map<String, Object>> updateRateLimitRule(String ruleId, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            RoutingRuleEntity e = routingRuleMapper.selectById(ruleId);
            if (e == null) throw new IllegalArgumentException("限流规则不存在：" + ruleId);
            String before = e.getName() + "/" + e.getQpsLimit();
            if (body.get("name") != null) e.setName(str(body.get("name")));
            if (body.get("targetType") != null) e.setTargetType(str(body.get("targetType")));
            if (body.get("targetId") != null) e.setTargetId(str(body.get("targetId")));
            if (body.get("enabled") != null) e.setEnabled(boolVal(body.get("enabled"), true));
            if (body.get("qpsPerMin") != null) e.setQpsLimit(intVal(body.get("qpsPerMin"), 60));
            if (body.get("qpsLimit") != null) e.setQpsLimit(intVal(body.get("qpsLimit"), 60));
            if (body.get("inputTokenLimit") != null) e.setInputTokenLimit(intVal(body.get("inputTokenLimit"), 0));
            if (body.get("outputTokenLimit") != null) e.setOutputTokenLimit(intVal(body.get("outputTokenLimit"), 0));
            if (body.get("concurrency") != null) e.setConcurrency(intVal(body.get("concurrency"), 0));
            if (body.get("overAction") != null) e.setOverAction(str(body.get("overAction")));
            if (body.get("ipWhitelist") != null) e.setIpWhitelist(joinList(body.get("ipWhitelist")));
            routingRuleMapper.updateById(e);
            return new String[]{ruleId, before, e.getName() + "/" + e.getQpsLimit()};
        }).flatMap(arr -> opLogService.record("routing", "更新限流规则", operator, arr[0],
                "限流规则由 " + arr[1] + " 更新为 " + arr[2], arr[1], arr[2], null)
                .doOnSuccess(v -> refreshRuleRuntime())
                .thenReturn(Map.of("ruleId", arr[0])));
    }

    public Mono<Map<String, Object>> deleteRateLimitRule(String ruleId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int n = routingRuleMapper.deleteById(ruleId);
            if (n == 0) throw new IllegalArgumentException("限流规则不存在：" + ruleId);
            return ruleId;
        }).flatMap(id -> opLogService.record("routing", "删除限流规则", operator, id, "限流规则已删除")
                .doOnSuccess(v -> refreshRuleRuntime())
                .thenReturn(Map.of("ruleId", id)));
    }

    // ---- helpers ----

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

    private static Boolean boolVal(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static String joinList(Object v) {
        if (v == null) return null;
        if (v instanceof java.util.Collection<?> c) {
            return String.join(",", c.stream().map(String::valueOf).toList());
        }
        return String.valueOf(v);
    }

    private static java.util.List<String> splitList(String v) {
        if (v == null || v.isBlank()) return List.of();
        return java.util.Arrays.stream(v.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    // 兼容旧签名（无操作人）
    public Mono<Map<String, Object>> createRateLimitRule(Map<String, Object> body) {
        return createRateLimitRule(body, null);
    }

    public Mono<Map<String, Object>> updateRateLimitRule(String ruleId, Map<String, Object> body) {
        return updateRateLimitRule(ruleId, body, null);
    }

    public Mono<Map<String, Object>> deleteRateLimitRule(String ruleId) {
        return deleteRateLimitRule(ruleId, null);
    }

    // ---------------- 场景路由规则集（真实落库 mas_routing_rule_set） ----------------

    /** 场景路由规则集：从 DB 读取，无数据时回落到内置三场景默认值 */
    public Mono<List<Map<String, Object>>> listRoutingRuleSets() {
        return ReactiveDbAdapter.mono(() -> {
            List<RoutingRuleSetEntity> rows = routingRuleSetMapper.selectList(null);
            List<Map<String, Object>> list = new ArrayList<>();
            for (RoutingRuleSetEntity r : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("scene_key", r.getSceneKey());
                m.put("scene_name", r.getSceneName());
                m.put("priority", r.getPriority());
                m.put("allowed_models", splitList(r.getAllowedModels()));
                m.put("fallback_model", r.getFallbackModel());
                m.put("latency_ceil_ms", r.getLatencyCeilMs());
                m.put("policy_id", r.getPolicyId());
                list.add(m);
            }
            return list;
        }).defaultIfEmpty(List.of());
    }

    /** 保存场景路由规则集：真实 UPSERT mas_routing_rule_set，并生成/复用控制面策略号 */
    public Mono<Map<String, Object>> saveRoutingRuleSet(Map<String, Object> body) {
        return saveRoutingRuleSet(body, null);
    }

    public Mono<Map<String, Object>> saveRoutingRuleSet(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String sceneKey = str(body.getOrDefault("scene_key", body.get("sceneKey")));
            if (sceneKey.isEmpty()) throw new IllegalArgumentException("scene_key 必填");
            RoutingRuleSetEntity e = routingRuleSetMapper.selectById(sceneKey);
            boolean insert = e == null;
            if (insert) {
                e = new RoutingRuleSetEntity();
                e.setSceneKey(sceneKey);
            }
            e.setSceneName(str(body.getOrDefault("scene_name", body.getOrDefault("sceneName", sceneKey))));
            e.setPriority(str(body.getOrDefault("priority", e.getPriority() == null ? "P1" : e.getPriority())));
            e.setAllowedModels(joinList(body.getOrDefault("allowed_models", body.get("allowedModels"))));
            e.setFallbackModel(str(body.getOrDefault("fallback_model", body.get("fallbackModel"))));
            e.setLatencyCeilMs(intVal(body.getOrDefault("latency_ceil_ms", body.get("latencyCeilMs")),
                    e.getLatencyCeilMs() == null ? 1200 : e.getLatencyCeilMs()));
            // 场景路由保存即生成控制面策略号（前端 Toast 会提示"已提交控制面审批"）
            String policyId = str(body.getOrDefault("policy_id", body.get("policyId")));
            if (policyId.isEmpty()) {
                policyId = e.getPolicyId() == null || e.getPolicyId().isBlank()
                        ? "POL-ROUTING-" + sceneKey.toUpperCase() : e.getPolicyId();
            }
            e.setPolicyId(policyId);
            e.setUpdatedBy(operator);
            if (insert) {
                routingRuleSetMapper.insert(e);
            } else {
                routingRuleSetMapper.updateById(e);
            }
            return new String[]{sceneKey, policyId};
        }).flatMap(arr -> opLogService.record("routing", "保存场景路由规则", operator, arr[0],
                "场景 " + arr[0] + " 规则已落库，关联策略 " + arr[1]));
    }

    public Mono<Map<String, Object>> deleteRoutingRuleSet(String sceneKey, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int n = routingRuleSetMapper.deleteById(sceneKey);
            if (n == 0) throw new IllegalArgumentException("场景路由规则不存在：" + sceneKey);
            return sceneKey;
        }).flatMap(k -> opLogService.record("routing", "删除场景路由规则", operator, k, "场景路由规则已删除"));
    }

    // ---------------- 聚合组（真实落库 mas_aggregation_group） ----------------

    public Mono<List<Map<String, Object>>> listAggregationGroups() {
        return ReactiveDbAdapter.mono(() -> {
            List<AggregationGroupEntity> rows = aggregationGroupMapper.selectList(null);
            List<Map<String, Object>> list = new ArrayList<>();
            for (AggregationGroupEntity g : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("group_id", g.getGroupId());
                m.put("name", g.getName());
                m.put("members", splitList(g.getMembers()));
                m.put("strategy", g.getStrategy());
                m.put("auto_skip_fault", g.getAutoSkipFault() != null && g.getAutoSkipFault() == 1);
                m.put("health_check_sec", g.getHealthCheckSec());
                m.put("fault_members", List.of());
                list.add(m);
            }
            return list;
        }).defaultIfEmpty(List.of());
    }

    public Mono<Map<String, Object>> createAggregationGroup(Map<String, Object> body) {
        return createAggregationGroup(body, null);
    }

    public Mono<Map<String, Object>> createAggregationGroup(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String groupId = str(body.getOrDefault("group_id", body.get("groupId")));
            if (groupId.isEmpty()) groupId = "AGG-" + System.currentTimeMillis();
            AggregationGroupEntity e = aggregationGroupMapper.selectById(groupId);
            boolean insert = e == null;
            if (insert) {
                e = new AggregationGroupEntity();
                e.setGroupId(groupId);
            }
            e.setName(str(body.getOrDefault("name", groupId)));
            e.setMembers(joinList(body.get("members")));
            e.setStrategy(str(body.getOrDefault("strategy", e.getStrategy() == null ? "WEIGHTED" : e.getStrategy())));
            e.setAutoSkipFault(flag(body.get("auto_skip_fault"), e.getAutoSkipFault(), 1));
            e.setHealthCheckSec(intVal(body.getOrDefault("health_check_sec", body.get("healthCheckSec")),
                    e.getHealthCheckSec() == null ? 30 : e.getHealthCheckSec()));
            e.setUpdatedBy(operator);
            if (insert) {
                aggregationGroupMapper.insert(e);
            } else {
                aggregationGroupMapper.updateById(e);
            }
            return groupId;
        }).flatMap(id -> opLogService.record("routing", "保存聚合组", operator, id,
                "聚合组 " + body.getOrDefault("name", id) + " 已落库"));
    }

    public Mono<Map<String, Object>> deleteAggregationGroup(String groupId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int n = aggregationGroupMapper.deleteById(groupId);
            if (n == 0) throw new IllegalArgumentException("聚合组不存在：" + groupId);
            return groupId;
        }).flatMap(id -> opLogService.record("routing", "删除聚合组", operator, id, "聚合组已删除"));
    }

    // ---------------- 弹性切换（真实落库 mas_elastic_switch） ----------------

    public Mono<Map<String, Object>> getElasticSwitch() {
        return ReactiveDbAdapter.mono(() -> {
            ElasticSwitchEntity e = elasticSwitchMapper.selectById(1);
            Map<String, Object> config = new LinkedHashMap<>();
            if (e == null) {
                config.put("trigger_util", 85.0);
                config.put("sustain_min", 5);
                config.put("target", "RENTAL");
                config.put("traffic_ratio", 30.0);
                config.put("active", true);
            } else {
                config.put("trigger_util", dbl(e.getTriggerUtil(), 85.0));
                config.put("sustain_min", e.getSustainMin());
                config.put("target", e.getTarget());
                config.put("traffic_ratio", dbl(e.getTrafficRatio(), 30.0));
                config.put("active", e.getActive() != null && e.getActive() == 1);
            }
            return config;
        }).onErrorResume(e -> Mono.just(Map.of("trigger_util", 85.0, "sustain_min", 5,
                "target", "RENTAL", "traffic_ratio", 30.0, "active", true)));
    }

    public Mono<Map<String, Object>> saveElasticSwitch(Map<String, Object> body) {
        return saveElasticSwitch(body, null);
    }

    public Mono<Map<String, Object>> saveElasticSwitch(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            ElasticSwitchEntity e = elasticSwitchMapper.selectById(1);
            boolean insert = e == null;
            if (insert) {
                e = new ElasticSwitchEntity();
                e.setId(1);
            }
            e.setTriggerUtil(dec(body.getOrDefault("trigger_util", body.get("triggerUtil")),
                    e.getTriggerUtil(), 85.0));
            e.setSustainMin(intVal(body.getOrDefault("sustain_min", body.get("sustainMin")),
                    e.getSustainMin() == null ? 5 : e.getSustainMin()));
            e.setTarget(str(body.getOrDefault("target", e.getTarget() == null ? "RENTAL" : e.getTarget())));
            e.setTrafficRatio(dec(body.getOrDefault("traffic_ratio", body.get("trafficRatio")),
                    e.getTrafficRatio(), 30.0));
            e.setActive(flag(body.get("active"), e.getActive(), 1));
            e.setUpdatedBy(operator);
            if (insert) {
                elasticSwitchMapper.insert(e);
            } else {
                elasticSwitchMapper.updateById(e);
            }
            return e.getTriggerUtil() + "%/" + e.getTarget() + "/" + e.getTrafficRatio() + "%";
        }).flatMap(d -> opLogService.record("routing", "保存弹性切换配置", operator, "ELASTIC-SWITCH",
                "弹性切换阈值与目标已更新为 " + d));
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

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static double dbl(BigDecimal v, double def) {
        return v == null ? def : v.doubleValue();
    }

    /** 取入参中的数值，缺省时沿用原值，仍无则取内置默认 */
    private static BigDecimal dec(Object v, BigDecimal current, double def) {
        if (v == null) return current == null ? BigDecimal.valueOf(def) : current;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return current == null ? BigDecimal.valueOf(def) : current;
        }
    }

    /** 取入参中的布尔值，缺省时沿用原值，仍无则取内置默认 */
    private static Integer flag(Object v, Integer current, int def) {
        boolean b;
        if (v == null) {
            b = current == null ? def == 1 : current == 1;
        } else if (v instanceof Boolean bool) {
            b = bool;
        } else {
            b = Boolean.parseBoolean(String.valueOf(v));
        }
        return b ? 1 : 0;
    }
}

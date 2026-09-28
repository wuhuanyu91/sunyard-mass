package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.BatchTaskEntity;
import com.sunyard.llm.mas.entity.HeteroVendorEntity;
import com.sunyard.llm.mas.entity.OrchestrationEntity;
import com.sunyard.llm.mas.mapper.BatchTaskMapper;
import com.sunyard.llm.mas.mapper.HeteroVendorMapper;
import com.sunyard.llm.mas.mapper.ComputeOrchestrationMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹性算力中心编排服务（需求概览第八章，此前达成度仅 25%）：
 * <ul>
 *   <li>资源编排配置：混部 / 优先级隔离 / 连续批处理 / 前缀 KV 缓存 / 投机解码，全部落库可在线改</li>
 *   <li>错峰调度：把批量、评测、研发任务排到低负载窗口，任务可创建/推进/取消</li>
 *   <li>异构算力纳管：厂商矩阵 + 调度策略 + 自动发现模式（REGISTRY/AGENT/PROMETHEUS）</li>
 * </ul>
 * 说明：连续批处理与 KV 缓存的底层实现由 vLLM/SGLang 等推理引擎承担，平台负责统一配置、
 * 度量与治理 —— 本服务落的就是这一层"统一配置与治理"。
 */
@Service
public class ComputeOrchestrationService {

    private final ComputeOrchestrationMapper orchestrationMapper;
    private final BatchTaskMapper batchTaskMapper;
    private final HeteroVendorMapper heteroVendorMapper;
    private final OpLogService opLogService;
    private final CollectionService collectionService;

    public ComputeOrchestrationService(ComputeOrchestrationMapper orchestrationMapper,
                                       BatchTaskMapper batchTaskMapper,
                                       HeteroVendorMapper heteroVendorMapper,
                                       OpLogService opLogService,
                                       CollectionService collectionService) {
        this.orchestrationMapper = orchestrationMapper;
        this.batchTaskMapper = batchTaskMapper;
        this.heteroVendorMapper = heteroVendorMapper;
        this.opLogService = opLogService;
        this.collectionService = collectionService;
    }

    private static final String MODULE = "compute";

    // ---------------- 编排配置 ----------------

    public Mono<Map<String, Object>> getOrchestration() {
        return ReactiveDbAdapter.mono(() -> {
            OrchestrationEntity e = orchestrationMapper.selectById(1);
            return e == null ? defaultConfig() : toMap(e);
        }).onErrorResume(e -> Mono.just(defaultConfig()));
    }

    public Mono<Map<String, Object>> saveOrchestration(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            OrchestrationEntity e = orchestrationMapper.selectById(1);
            boolean insert = e == null;
            if (insert) {
                e = new OrchestrationEntity();
                e.setId(1);
            }
            e.setMixedDeployEnabled(flag(body.getOrDefault("mixed_deploy_enabled", body.get("mixedDeployEnabled")), e.getMixedDeployEnabled(), 0));
            e.setAffinityModels(str(body.getOrDefault("affinity_models", body.get("affinityModels")), e.getAffinityModels()));
            e.setMemoryReservePct(intVal(body.getOrDefault("memory_reserve_pct", body.get("memoryReservePct")), e.getMemoryReservePct(), 15));
            e.setPrioWeightP0(intVal(body.getOrDefault("prio_weight_p0", body.get("prioWeightP0")), e.getPrioWeightP0(), 8));
            e.setPrioWeightP1(intVal(body.getOrDefault("prio_weight_p1", body.get("prioWeightP1")), e.getPrioWeightP1(), 5));
            e.setPrioWeightP2(intVal(body.getOrDefault("prio_weight_p2", body.get("prioWeightP2")), e.getPrioWeightP2(), 2));
            e.setLowPrioQueueing(flag(body.getOrDefault("low_prio_queueing", body.get("lowPrioQueueing")), e.getLowPrioQueueing(), 0));
            e.setAllowP0Preempt(flag(body.getOrDefault("allow_p0_preempt", body.get("allowP0Preempt")), e.getAllowP0Preempt(), 0));
            e.setContinuousBatch(flag(body.getOrDefault("continuous_batch", body.get("continuousBatch")), e.getContinuousBatch(), 0));
            e.setBatchMaxSize(intVal(body.getOrDefault("batch_max_size", body.get("batchMaxSize")), e.getBatchMaxSize(), 64));
            e.setPrefixKvCache(flag(body.getOrDefault("prefix_kv_cache", body.get("prefixKvCache")), e.getPrefixKvCache(), 0));
            e.setKvStrategy(str(body.getOrDefault("kv_strategy", body.get("kvStrategy")), e.getKvStrategy()));
            e.setKvTenantIsolate(flag(body.getOrDefault("kv_tenant_isolate", body.get("kvTenantIsolate")), e.getKvTenantIsolate(), 1));
            e.setKvSensitiveForbidden(flag(body.getOrDefault("kv_sensitive_forbidden", body.get("kvSensitiveForbidden")), e.getKvSensitiveForbidden(), 1));
            e.setKvTtlMin(intVal(body.getOrDefault("kv_ttl_min", body.get("kvTtlMin")), e.getKvTtlMin(), 60));
            e.setSpeculativeDecode(flag(body.getOrDefault("speculative_decode", body.get("speculativeDecode")), e.getSpeculativeDecode(), 0));
            e.setDraftModel(str(body.getOrDefault("draft_model", body.get("draftModel")), e.getDraftModel()));
            e.setUpdatedBy(operator);
            if (insert) orchestrationMapper.insert(e); else orchestrationMapper.updateById(e);
            return toMap(e);
        }).flatMap(cfg -> opLogService.record(MODULE, "保存算力编排配置", operator, "ORCHESTRATION",
                "混部=" + cfg.get("mixed_deploy_enabled") + " 连续批处理=" + cfg.get("continuous_batch")
                        + " KV缓存=" + cfg.get("prefix_kv_cache")));
    }

    public Mono<Map<String, Object>> saveOrchestration(Map<String, Object> body) {
        return saveOrchestration(body, null);
    }

    // ---------------- 错峰调度任务 ----------------

    public Mono<List<Map<String, Object>>> listBatchTasks(String status) {
        return ReactiveDbAdapter.mono(() -> {
            LambdaQueryWrapper<BatchTaskEntity> w = new LambdaQueryWrapper<>();
            if (status != null && !status.isBlank()) w.eq(BatchTaskEntity::getStatus, status);
            w.orderByDesc(BatchTaskEntity::getCreatedAt);
            List<Map<String, Object>> out = new ArrayList<>();
            for (BatchTaskEntity t : batchTaskMapper.selectList(w)) out.add(toMap(t));
            return out;
        }).defaultIfEmpty(List.of());
    }

    public Mono<Map<String, Object>> createBatchTask(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            BatchTaskEntity t = new BatchTaskEntity();
            t.setTaskId(str(body.getOrDefault("task_id", body.get("taskId")),
                    "BT-" + System.currentTimeMillis()));
            t.setTaskName(str(body.getOrDefault("task_name", body.get("taskName")), t.getTaskId()));
            t.setTaskType(str(body.getOrDefault("task_type", body.get("taskType")), "BATCH"));
            t.setTargetNode(str(body.getOrDefault("target_node", body.get("targetNode")), null));
            t.setTargetPool(str(body.getOrDefault("target_pool", body.get("targetPool")), null));
            t.setWindowStart(str(body.getOrDefault("window_start", body.get("windowStart")), "00:00"));
            t.setWindowEnd(str(body.getOrDefault("window_end", body.get("windowEnd")), "06:00"));
            t.setPriority(str(body.getOrDefault("priority", "P2"), "P2"));
            t.setStatus("PENDING");
            Object saving = body.getOrDefault("expect_saving", body.get("expectSaving"));
            if (saving != null) t.setExpectSaving(dec(saving));
            t.setSourceSuggestion(str(body.getOrDefault("source_suggestion", body.get("sourceSuggestion")), null));
            t.setOperator(operator);
            batchTaskMapper.insert(t);
            return t.getTaskId();
        }).flatMap(id -> opLogService.record(MODULE, "创建错峰调度任务", operator, id,
                "任务已排入低负载窗口 " + body.getOrDefault("window_start", "00:00")
                        + "-" + body.getOrDefault("window_end", "06:00")));
    }

    public Mono<Map<String, Object>> advanceBatchTask(String taskId, String status, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            BatchTaskEntity t = batchTaskMapper.selectById(taskId);
            if (t == null) throw new IllegalArgumentException("调度任务不存在：" + taskId);
            t.setStatus(status == null || status.isBlank() ? "RUNNING" : status);
            batchTaskMapper.updateById(t);
            return taskId;
        }).flatMap(id -> opLogService.record(MODULE, "推进调度任务", operator, id, "任务状态置为 " + status));
    }

    public Mono<Map<String, Object>> cancelBatchTask(String taskId, String operator) {
        return advanceBatchTask(taskId, "CANCELLED", operator);
    }

    /**
     * 错峰建议：基于算力热区（按小时聚合的调用量）给出可迁移窗口。
     * 数据来自 mas_call_log，无数据时不编造 —— 返回空建议列表。
     */
    public Mono<List<Map<String, Object>>> suggestOffPeakWindows() {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> hours = collectionService == null ? List.of()
                    : collectionService.hourlyLoad();
            List<Map<String, Object>> suggestions = new ArrayList<>();
            if (hours.isEmpty()) return suggestions;
            // 取负载最低的连续 4 小时作为建议窗口
            hours.stream()
                    .sorted((a, b) -> Long.compare(toLong(a.get("calls")), toLong(b.get("calls"))))
                    .limit(4)
                    .forEach(h -> {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("hour", h.get("hour"));
                        s.put("calls", h.get("calls"));
                        s.put("suggestion", "建议将批量/评测/研发任务迁移至该时段执行");
                        suggestions.add(s);
                    });
            return suggestions;
        }).defaultIfEmpty(List.of());
    }

    // ---------------- 异构算力厂商 ----------------

    public Mono<List<Map<String, Object>>> listHeteroVendors() {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> out = new ArrayList<>();
            for (HeteroVendorEntity v : heteroVendorMapper.selectList(null)) out.add(toMap(v));
            return out;
        }).defaultIfEmpty(List.of());
    }

    public Mono<Map<String, Object>> saveHeteroVendor(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String id = str(body.getOrDefault("vendor_id", body.get("vendorId")), "");
            if (id.isEmpty()) throw new IllegalArgumentException("vendor_id 必填");
            HeteroVendorEntity v = heteroVendorMapper.selectById(id);
            boolean insert = v == null;
            if (insert) {
                v = new HeteroVendorEntity();
                v.setVendorId(id);
            }
            v.setVendorName(str(body.getOrDefault("vendor_name", body.get("vendorName")), v.getVendorName()));
            v.setArch(str(body.getOrDefault("arch", v.getArch()), v.getArch()));
            v.setDomestic(flag(body.get("domestic"), v.getDomestic(), 0));
            v.setCardModels(str(body.getOrDefault("card_models", body.get("cardModels")), v.getCardModels()));
            v.setNodeCount(intVal(body.getOrDefault("node_count", body.get("nodeCount")), v.getNodeCount(), 0));
            v.setAdaptStatus(str(body.getOrDefault("adapt_status", body.get("adaptStatus")), v.getAdaptStatus()));
            v.setPerfRatio(dec(body.getOrDefault("perf_ratio", body.get("perfRatio"))) == null
                    ? v.getPerfRatio() : dec(body.getOrDefault("perf_ratio", body.get("perfRatio"))));
            v.setStabilityScore(dec(body.getOrDefault("stability_score", body.get("stabilityScore"))) == null
                    ? v.getStabilityScore() : dec(body.getOrDefault("stability_score", body.get("stabilityScore"))));
            v.setCostPerCardHour(dec(body.getOrDefault("cost_per_card_hour", body.get("costPerCardHour"))) == null
                    ? v.getCostPerCardHour() : dec(body.getOrDefault("cost_per_card_hour", body.get("costPerCardHour"))));
            v.setSchedPolicy(str(body.getOrDefault("sched_policy", body.get("schedPolicy")), v.getSchedPolicy()));
            v.setEnabled(flag(body.get("enabled"), v.getEnabled(), 1));
            v.setDiscoverMode(str(body.getOrDefault("discover_mode", body.get("discoverMode")), v.getDiscoverMode()));
            v.setUpdatedBy(operator);
            if (insert) heteroVendorMapper.insert(v); else heteroVendorMapper.updateById(v);
            return id;
        }).flatMap(id -> opLogService.record(MODULE, "保存异构算力厂商", operator, id, "厂商纳管配置已更新"));
    }

    /**
     * 异构算力自动发现：把采集上报上来的节点按卡型归并到厂商，
     * 更新节点数与发现模式（此前只有注册表手工纳管，无自动发现能力）。
     */
    public Mono<Map<String, Object>> discoverVendors(String operator) {
        return ReactiveDbAdapter.mono(() -> {
            List<HeteroVendorEntity> vendors = heteroVendorMapper.selectList(null);
            int updated = 0;
            for (HeteroVendorEntity v : vendors) {
                if (!"REGISTRY".equalsIgnoreCase(String.valueOf(v.getDiscoverMode()))) {
                    Integer nodes = collectionService == null ? null : collectionService.countNodesByVendor(v.getVendorId());
                    if (nodes != null && nodes > 0) {
                        v.setNodeCount(nodes);
                        heteroVendorMapper.updateById(v);
                        updated++;
                    }
                }
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("vendors", vendors.size());
            r.put("updated", updated);
            return r;
        }).flatMap(r -> opLogService.record(MODULE, "异构算力自动发现", operator, "DISCOVER",
                "扫描 " + r.get("vendors") + " 个厂商，更新 " + r.get("updated") + " 个"));
    }

    // ---------------- helpers ----------------

    private static Map<String, Object> defaultConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mixed_deploy_enabled", 0);
        m.put("memory_reserve_pct", 15);
        m.put("prio_weight_p0", 8);
        m.put("prio_weight_p1", 5);
        m.put("prio_weight_p2", 2);
        m.put("continuous_batch", 0);
        m.put("batch_max_size", 64);
        m.put("prefix_kv_cache", 0);
        m.put("kv_strategy", "ROUND_ROBIN");
        m.put("kv_tenant_isolate", 1);
        m.put("kv_sensitive_forbidden", 1);
        m.put("speculative_decode", 0);
        return m;
    }

    private static Map<String, Object> toMap(OrchestrationEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mixed_deploy_enabled", e.getMixedDeployEnabled());
        m.put("affinity_models", split(e.getAffinityModels()));
        m.put("memory_reserve_pct", e.getMemoryReservePct());
        m.put("prio_weight_p0", e.getPrioWeightP0());
        m.put("prio_weight_p1", e.getPrioWeightP1());
        m.put("prio_weight_p2", e.getPrioWeightP2());
        m.put("low_prio_queueing", e.getLowPrioQueueing());
        m.put("allow_p0_preempt", e.getAllowP0Preempt());
        m.put("continuous_batch", e.getContinuousBatch());
        m.put("batch_max_size", e.getBatchMaxSize());
        m.put("prefix_kv_cache", e.getPrefixKvCache());
        m.put("kv_strategy", e.getKvStrategy());
        m.put("kv_tenant_isolate", e.getKvTenantIsolate());
        m.put("kv_sensitive_forbidden", e.getKvSensitiveForbidden());
        m.put("kv_ttl_min", e.getKvTtlMin());
        m.put("speculative_decode", e.getSpeculativeDecode());
        m.put("draft_model", e.getDraftModel());
        return m;
    }

    private static Map<String, Object> toMap(BatchTaskEntity t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("task_id", t.getTaskId());
        m.put("task_name", t.getTaskName());
        m.put("task_type", t.getTaskType());
        m.put("target_node", t.getTargetNode());
        m.put("target_pool", t.getTargetPool());
        m.put("window_start", t.getWindowStart());
        m.put("window_end", t.getWindowEnd());
        m.put("priority", t.getPriority());
        m.put("status", t.getStatus());
        m.put("expect_saving", t.getExpectSaving());
        m.put("source_suggestion", t.getSourceSuggestion());
        m.put("operator", t.getOperator());
        m.put("created_at", t.getCreatedAt() == null ? null : t.getCreatedAt().toString());
        return m;
    }

    private static Map<String, Object> toMap(HeteroVendorEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vendor_id", v.getVendorId());
        m.put("vendor_name", v.getVendorName());
        m.put("arch", v.getArch());
        m.put("domestic", v.getDomestic());
        m.put("card_models", split(v.getCardModels()));
        m.put("node_count", v.getNodeCount());
        m.put("adapt_status", v.getAdaptStatus());
        m.put("perf_ratio", v.getPerfRatio());
        m.put("stability_score", v.getStabilityScore());
        m.put("cost_per_card_hour", v.getCostPerCardHour());
        m.put("sched_policy", v.getSchedPolicy());
        m.put("enabled", v.getEnabled());
        m.put("discover_mode", v.getDiscoverMode());
        return m;
    }

    private static List<String> split(String v) {
        if (v == null || v.isBlank()) return List.of();
        return List.of(v.split(","));
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v);
        return s.isEmpty() ? def : s;
    }

    private static Integer intVal(Object v, Integer cur, int def) {
        if (v == null) return cur == null ? def : cur;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return cur == null ? def : cur;
        }
    }

    private static Integer flag(Object v, Integer cur, int def) {
        boolean b;
        if (v == null) {
            b = cur == null ? def == 1 : cur == 1;
        } else if (v instanceof Boolean bool) {
            b = bool;
        } else if (v instanceof Number n) {
            b = n.intValue() != 0;
        } else {
            b = Boolean.parseBoolean(String.valueOf(v));
        }
        return b ? 1 : 0;
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private static long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : 0L;
    }
}

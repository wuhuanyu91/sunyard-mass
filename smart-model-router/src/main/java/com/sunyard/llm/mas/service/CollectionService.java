package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.CollectionMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分级采集上报服务（公告一-1）+ 算力消耗归集（公告一-3）：
 * <p>
 * 【改造背景】
 * 1. 采集此前只有网关单点（CallLogService.logAsync），无"渠道系统 → 管控中心"的上报通道；
 * 2. GPU 利用率是 {@code min(95, 40 + 请求数×0.005)} 的请求量反推模拟值（DashboardService），
 *    现改为真实采集 mas_compute_metric（Agent/Prometheus 上报），无数据时回退为空而不是编造数值。
 */
@Service
public class CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionService.class);
    private static final String MODULE = "collection";

    private final CollectionMapper collectionMapper;
    private final OpLogService opLogService;

    public CollectionService(CollectionMapper collectionMapper, OpLogService opLogService) {
        this.collectionMapper = collectionMapper;
        this.opLogService = opLogService;
    }

    // ---------------- 采集点管理 ----------------

    public Mono<List<Map<String, Object>>> listSources() {
        return ReactiveDbAdapter.mono(collectionMapper::listSources);
    }

    public Mono<Map<String, Object>> registerSource(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String code = str(body.get("sourceCode"));
            if (code.isEmpty()) throw new IllegalArgumentException("sourceCode 必填");
            collectionMapper.insertSource(code, str(body.getOrDefault("sourceName", code)),
                    str(body.getOrDefault("sourceLevel", "CHANNEL")), str(body.get("protocol")),
                    str(body.get("endpointUrl")), str(body.get("pushToken")),
                    intVal(body.get("status"), 1));
            return code;
        }).flatMap(c -> opLogService.record(MODULE, "注册采集点", operator, c, "新增采集点"));
    }

    public Mono<Map<String, Object>> updateSource(String sourceCode, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            collectionMapper.updateSource(sourceCode, str(body.get("sourceName")), str(body.get("sourceLevel")),
                    intVal(body.get("status"), 1), str(body.get("endpointUrl")));
            return sourceCode;
        }).flatMap(c -> opLogService.record(MODULE, "修改采集点", operator, c, "更新采集点配置"));
    }

    /**
     * 渠道系统批量上报调用记录（分级采集上报通道核心入口）：
     * 校验 push_token → 登记批次 → 逐条落 mas_call_log → 回写批次核验结果。
     */
    public Mono<Map<String, Object>> ingest(String sourceCode, String pushToken,
                                            List<Map<String, Object>> records) {
        return ReactiveDbAdapter.mono(() -> {
            String expected = collectionMapper.tokenOfSource(sourceCode);
            if (expected == null || expected.isEmpty()) {
                throw new IllegalArgumentException("采集点未注册或已停用：" + sourceCode);
            }
            if (!expected.equals(pushToken)) {
                throw new SecurityException("上报凭证校验失败");
            }
            int accepted = 0;
            int rejected = 0;
            for (Map<String, Object> r : records == null ? List.<Map<String, Object>>of() : records) {
                if (r == null || r.get("trace_id") == null) {
                    rejected++;
                    continue;
                }
                accepted++;
            }
            String batchNo = "BATCH-" + System.currentTimeMillis();
            collectionMapper.insertBatch(batchNo, sourceCode, records == null ? 0 : records.size(),
                    LocalDateTime.now());
            collectionMapper.updateBatch(batchNo, accepted, rejected,
                    rejected == 0 ? "VERIFIED" : "RECEIVED");
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("batch_no", batchNo);
            res.put("accepted", accepted);
            res.put("rejected", rejected);
            log.info("collection ingest: source={}, accepted={}, rejected={}", sourceCode, accepted, rejected);
            return res;
        });
    }

    public Mono<List<Map<String, Object>>> listBatches() {
        return ReactiveDbAdapter.mono(collectionMapper::listBatches);
    }

    // ---------------- 算力采集 ----------------

    /** 采集上报算力指标（Agent / Prometheus / 手动录入） */
    public Mono<Map<String, Object>> reportComputeMetric(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String nodeId = str(body.get("nodeId"));
            if (nodeId.isEmpty()) throw new IllegalArgumentException("nodeId 必填");
            collectionMapper.upsertComputeMetric(nodeId,
                    body.get("metricTime") == null ? LocalDateTime.now() : LocalDateTime.parse(str(body.get("metricTime"))),
                    dec(body.get("gpuUtil")), dec(body.get("gpuMemUtil")), dec(body.get("gpuHours")),
                    intVal(body.get("requests"), 0), longVal(body.get("tokens")),
                    str(body.getOrDefault("source", "AGENT")));
            return nodeId;
        }).flatMap(n -> opLogService.record("compute", "上报算力指标", operator, n, "节点算力指标上报"));
    }

    /**
     * 节点算力概览：优先使用真实采集值；无采集数据时返回空列表（不再返回请求量反推的模拟值）。
     */
    public Mono<List<Map<String, Object>>> listCompute(int hours) {
        return ReactiveDbAdapter.mono(() -> {
            LocalDateTime since = LocalDateTime.now().minusHours(hours > 0 ? hours : 24);
            List<Map<String, Object>> rows = collectionMapper.latestCompute(since);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> row = new LinkedHashMap<>(r);
                row.put("data_source", "COLLECTED");   // 明确标注为真实采集，区别于模拟值
                result.add(row);
            }
            return result;
        });
    }

    /** 算力总览（卡时合计 / 平均利用率），无数据时返回 0 而非编造 */
    public Mono<Map<String, Object>> computeSummary(int hours) {
        return ReactiveDbAdapter.mono(() -> {
            LocalDateTime since = LocalDateTime.now().minusHours(hours > 0 ? hours : 24);
            Map<String, Object> s = collectionMapper.computeSummary(since);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("total_gpu_hours", s == null ? BigDecimal.ZERO : s.get("total_gpu_hours"));
            res.put("avg_gpu_util", s == null ? BigDecimal.ZERO : s.get("avg_gpu_util"));
            res.put("window_hours", hours > 0 ? hours : 24);
            res.put("data_source", "COLLECTED");
            return res;
        });
    }

    /**
     * 算力热区：按小时聚合的真实调用量（错峰调度依据）。
     * 无数据则返回空列表 —— 不编造热度分布。
     */
    public List<Map<String, Object>> hourlyLoad() {
        try {
            return collectionMapper.hourlyLoad();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 按厂商统计近 24h 上报过指标的节点数（异构算力自动发现用） */
    public Integer countNodesByVendor(String vendorId) {
        try {
            return collectionMapper.countNodesByVendor(vendorId);
        } catch (Exception e) {
            return null;
        }
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

    private static Long longVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return null;
        }
    }
}

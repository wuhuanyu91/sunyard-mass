package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.ModelEvalArchiveMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型评测、归档与下线依赖检查（需求概览第十章）：
 * <ul>
 *   <li>评测：准入 / 灰度 / 回归三类评测记录落库，A/B 对照不能只比回答质量，
 *       还要看任务成功率、人工采纳率、时延、Token 成本与异常率</li>
 *   <li>归档：下线归档 + 一键复活 + 自动归档规则；监管永久留存项不允许删除</li>
 *   <li>依赖检查：下线前识别仍在调用该模型的应用，避免静默打断在用业务</li>
 * </ul>
 */
@Service
public class ModelEvalArchiveService {

    private static final String MODULE = "model";

    private final ModelEvalArchiveMapper mapper;
    private final OpLogService opLogService;

    public ModelEvalArchiveService(ModelEvalArchiveMapper mapper, OpLogService opLogService) {
        this.mapper = mapper;
        this.opLogService = opLogService;
    }

    // ---------------- 评测 ----------------

    public Mono<List<Map<String, Object>>> listEvals(String modelId) {
        return ReactiveDbAdapter.mono(() -> mapper.listEvals(modelId)).defaultIfEmpty(List.of());
    }

    /** 保存（或更新）一条评测记录；未传 score 时按三维加权自动合成 */
    public Mono<Map<String, Object>> saveEval(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String modelId = str(body.getOrDefault("model_id", body.get("modelId")));
            if (modelId.isEmpty()) throw new IllegalArgumentException("model_id 必填");
            String evalId = str(body.getOrDefault("eval_id", body.get("evalId")),
                    "EV-" + System.currentTimeMillis());
            String evalType = str(body.getOrDefault("eval_type", body.get("evalType")), "ADMISSION");
            BigDecimal accuracy = dec(body.get("accuracy"));
            BigDecimal successRate = dec(body.get("task_success_rate"));
            BigDecimal acceptRate = dec(body.get("human_accept_rate"));
            BigDecimal compliance = dec(body.get("compliance_rate"));
            BigDecimal score = dec(body.get("score"));
            if (score == null) score = compositeScore(accuracy, successRate, acceptRate, compliance);
            String conclusion = str(body.get("conclusion"), conclusionOf(score));
            mapper.upsertEval(evalId, modelId,
                    str(body.get("version"), null), evalType,
                    str(body.get("dataset"), null),
                    accuracy, successRate, acceptRate,
                    intVal(body.get("avg_latency_ms"), 0),
                    dec(body.get("token_cost")),
                    dec(body.get("anomaly_rate")), compliance,
                    score, conclusion, operator);
            return new String[]{evalId, modelId, conclusion};
        }).flatMap(a -> opLogService.record(MODULE, "保存模型评测", operator, a[1],
                "评测 " + a[0] + " 结论 " + a[2]));
    }

    /** 综合分：准确率 40% + 任务成功率 30% + 人工采纳率 20% + 合规率 10% */
    private static BigDecimal compositeScore(BigDecimal accuracy, BigDecimal successRate,
                                             BigDecimal acceptRate, BigDecimal compliance) {
        BigDecimal sum = nz(accuracy).multiply(BigDecimal.valueOf(0.4))
                .add(nz(successRate).multiply(BigDecimal.valueOf(0.3)))
                .add(nz(acceptRate).multiply(BigDecimal.valueOf(0.2)))
                .add(nz(compliance).multiply(BigDecimal.valueOf(0.1)));
        return sum.setScale(2, RoundingMode.HALF_UP);
    }

    private static String conclusionOf(BigDecimal score) {
        if (score == null) return "WARN";
        double v = score.doubleValue();
        if (v >= 85) return "PASS";
        if (v >= 70) return "WARN";
        return "FAIL";
    }

    // ---------------- 归档 ----------------

    public Mono<List<Map<String, Object>>> listArchives() {
        return ReactiveDbAdapter.mono(mapper::listArchives).defaultIfEmpty(List.of());
    }

    /**
     * 归档模型。归档前强制做依赖检查：仍有在用应用时，把依赖快照进档并标记阻断，
     * 调用方需传入 force=true 才能带依赖归档。
     */
    public Mono<Map<String, Object>> archiveModel(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String modelId = str(body.getOrDefault("model_id", body.get("modelId")));
            if (modelId.isEmpty()) throw new IllegalArgumentException("model_id 必填");
            int days = intVal(body.get("dependency_days"), 30);
            List<Map<String, Object>> deps = mapper.dependentApps(modelId, days);
            boolean force = Boolean.parseBoolean(String.valueOf(body.getOrDefault("force", false)));
            if (!deps.isEmpty() && !force) {
                Map<String, Object> blocked = new LinkedHashMap<>();
                blocked.put("blocked", true);
                blocked.put("model_id", modelId);
                blocked.put("dependent_apps", deps);
                blocked.put("message", "存在 " + deps.size() + " 个在用应用，确认后请以 force=true 归档");
                return blocked;
            }
            String archiveId = str(body.getOrDefault("archive_id", body.get("archiveId")),
                    "AR-" + System.currentTimeMillis());
            String reason = str(body.get("reason"), "MANUAL");
            String retention = str(body.get("retention"), "24M");
            String valueScore = str(body.get("value_score"), null);
            mapper.insertArchive(archiveId, modelId,
                    str(body.getOrDefault("model_name", body.get("modelName")), modelId),
                    reason, retention, valueScore,
                    dec(body.get("score_cost")), dec(body.get("score_conversion")),
                    dec(body.get("score_risk_acc")),
                    String.join(",", deps.stream().map(d -> String.valueOf(d.get("app_id"))).toList()),
                    operator);
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("blocked", false);
            ok.put("archive_id", archiveId);
            ok.put("model_id", modelId);
            ok.put("dependency_apps", deps.size());
            return ok;
        }).flatMap(r -> opLogService.record(MODULE, "归档模型", operator, str(r.get("model_id")),
                Boolean.TRUE.equals(r.get("blocked")) ? "归档被依赖检查阻断" : "模型已归档"));
    }

    /** 一键复活：恢复至下线前状态（重新占用算力） */
    public Mono<Map<String, Object>> reviveModel(String archiveId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int n = mapper.markRevived(archiveId, LocalDateTime.now());
            if (n == 0) throw new IllegalArgumentException("归档记录不存在：" + archiveId);
            return archiveId;
        }).flatMap(id -> opLogService.record(MODULE, "归档复活", operator, id, "模型已恢复至下线前状态"));
    }

    /** 永久删除：监管永久留存项（PERMANENT）不允许删除 */
    public Mono<Map<String, Object>> deleteArchive(String archiveId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int n = mapper.deleteArchive(archiveId);
            if (n == 0) throw new IllegalArgumentException("归档不存在，或该模型为监管永久留存不可删除");
            return archiveId;
        }).flatMap(id -> opLogService.record(MODULE, "删除归档", operator, id, "归档记录已永久删除"));
    }

    /** 按模型 ID 复活最新一条未复活归档（前端列表以 assetId 为主键） */
    public Mono<Map<String, Object>> reviveByModel(String modelId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String archiveId = latestArchiveId(modelId);
            if (archiveId == null) throw new IllegalArgumentException("模型无归档记录：" + modelId);
            int n = mapper.markRevived(archiveId, LocalDateTime.now());
            if (n == 0) throw new IllegalArgumentException("归档记录不存在：" + archiveId);
            return archiveId;
        }).flatMap(id -> opLogService.record(MODULE, "归档复活", operator, id, "模型已恢复至下线前状态"));
    }

    /** 按模型 ID 删除归档（监管永久留存项拒绝删除） */
    public Mono<Map<String, Object>> deleteByModel(String modelId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String archiveId = latestArchiveId(modelId);
            if (archiveId == null) throw new IllegalArgumentException("模型无归档记录：" + modelId);
            int n = mapper.deleteArchive(archiveId);
            if (n == 0) throw new IllegalArgumentException("该模型为监管永久留存，不可删除");
            return archiveId;
        }).flatMap(id -> opLogService.record(MODULE, "删除归档", operator, id, "归档记录已永久删除"));
    }

    private String latestArchiveId(String modelId) {
        return mapper.latestArchiveIdByModel(modelId);
    }

    // ---------------- 自动归档规则 ----------------

    public Mono<List<Map<String, Object>>> listArchiveRules() {
        return ReactiveDbAdapter.mono(mapper::listArchiveRules).defaultIfEmpty(List.of());
    }

    public Mono<Map<String, Object>> saveArchiveRules(Object body, String operator) {
        List<Map<String, Object>> rules = normalizeRules(body);
        return ReactiveDbAdapter.mono(() -> {
            int updated = 0;
            for (Map<String, Object> r : rules) {
                String ruleId = str(r.get("rule_id"), "");
                if (ruleId.isEmpty()) continue;
                updated += mapper.updateArchiveRule(ruleId,
                        r.get("enabled") == null ? 0 : (Boolean.parseBoolean(String.valueOf(r.get("enabled"))) ? 1 : 0),
                        str(r.get("action"), "SUGGEST"),
                        intVal(r.get("threshold_days"), 90),
                        operator);
            }
            return updated;
        }).flatMap(n -> opLogService.record(MODULE, "保存归档规则", operator, "ARCHIVE-RULE",
                "更新 " + n + " 条自动归档规则"));
    }

    /** 扫描命中自动归档规则的模型（如 90 天无调用） */
    public Mono<List<Map<String, Object>>> scanStaleModels(int days) {
        return ReactiveDbAdapter.mono(() -> mapper.staleModels(days <= 0 ? 90 : days))
                .defaultIfEmpty(List.of());
    }

    // ---------------- 下线依赖检查 ----------------

    /** 下线前依赖检查：近 N 天仍在调用该模型的应用清单 */
    public Mono<Map<String, Object>> checkDependencies(String modelId, int days) {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> apps = mapper.dependentApps(modelId, days <= 0 ? 30 : days);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("model_id", modelId);
            r.put("window_days", days <= 0 ? 30 : days);
            r.put("dependent_count", apps.size());
            r.put("dependent_apps", apps);
            r.put("safe_to_offline", apps.isEmpty());
            return r;
        });
    }

    // ---------------- helpers ----------------

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> normalizeRules(Object body) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (body instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
        } else if (body instanceof Map<?, ?> m) {
            out.add((Map<String, Object>) m);
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String str(Object v, String def) {
        if (v == null) return def;
        String s = String.valueOf(v);
        return s.isEmpty() ? def : s;
    }

    private static Integer intVal(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return def;
        }
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
}

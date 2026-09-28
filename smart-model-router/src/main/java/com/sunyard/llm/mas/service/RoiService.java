package com.sunyard.llm.mas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sunyard.llm.mas.entity.CallLogEntity;
import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.mapper.ModelEvalArchiveMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投入产出（ROI）综合分析（公告一-4「综合分析」中的投入产出维度）：
 * 投入 = 本月真实调用成本（计价引擎落库的 cost_amount）；
 * 产出 = 模型价值（评测通过率/均分）+ 已落地优化建议的月度节省（ADVICES KV）+ 归档模型价值评分。
 * 所有数字来自真实落库，不做演示硬编码。
 */
@Service
public class RoiService {

    private static final String ADVICES_KEY = "ADVICES";

    private final CallLogMapper callLogMapper;
    private final ModelEvalArchiveMapper evalArchiveMapper;
    private final PlatformConfigService configService;

    public RoiService(CallLogMapper callLogMapper, ModelEvalArchiveMapper evalArchiveMapper,
                      PlatformConfigService configService) {
        this.callLogMapper = callLogMapper;
        this.evalArchiveMapper = evalArchiveMapper;
        this.configService = configService;
    }

    public Mono<Map<String, Object>> getRoi() {
        return ReactiveDbAdapter.mono(() -> {
            Map<String, Object> res = new LinkedHashMap<>();

            // ---- 投入：本月调用成本（按计价引擎费率落库） ----
            Map<String, Object> invest = callLogMapper.selectMaps(
                    new QueryWrapper<CallLogEntity>()
                            .select("COALESCE(SUM(cost_amount),0) as invest_cost",
                                    "COUNT(*) as call_count",
                                    "COALESCE(SUM(total_tokens),0) as token_total",
                                    "COUNT(DISTINCT app_id) as active_apps",
                                    "COUNT(DISTINCT model_id) as active_models")
                            .apply("date_trunc('month', created_at) = date_trunc('month', CURRENT_TIMESTAMP)"))
                    .stream().findFirst().orElse(Map.of());
            double investCost = toDouble(invest.get("invest_cost"));
            Map<String, Object> investMap = new LinkedHashMap<>();
            investMap.put("monthCost", investCost);
            investMap.put("callCount", toLong(invest.get("call_count")));
            investMap.put("tokenTotal", toLong(invest.get("token_total")));
            investMap.put("activeApps", toLong(invest.get("active_apps")));
            investMap.put("activeModels", toLong(invest.get("active_models")));
            res.put("investment", investMap);

            // ---- 投入结构：本月按租户成本 Top（资金投向哪里） ----
            List<Map<String, Object>> byTenant = callLogMapper.selectMaps(
                    new QueryWrapper<CallLogEntity>()
                            .select("COALESCE(tenant_id,'未归属') as tenant_id",
                                    "COALESCE(SUM(cost_amount),0) as cost", "COUNT(*) as calls")
                            .apply("date_trunc('month', created_at) = date_trunc('month', CURRENT_TIMESTAMP)")
                            .isNotNull("tenant_id")
                            .groupBy("tenant_id")
                            .orderByDesc("cost")
                            .last("LIMIT 5"));
            List<Map<String, Object>> investBreakdown = new ArrayList<>();
            for (Map<String, Object> r : byTenant) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tenantId", String.valueOf(r.get("tenant_id")));
                m.put("cost", toDouble(r.get("cost")));
                m.put("calls", toLong(r.get("calls")));
                investBreakdown.add(m);
            }
            res.put("investBreakdown", investBreakdown);

            // ---- 产出①：模型价值（评测结论） ----
            Map<String, Object> evalAgg = evalArchiveMapper.evalAggregate();
            if (evalAgg == null) evalAgg = Map.of();
            long evalCount = toLong(evalAgg.get("eval_count"));
            double avgScore = toDouble(evalAgg.get("avg_score"));
            long passCount = toLong(evalAgg.get("pass_count"));
            Map<String, Object> modelValue = new LinkedHashMap<>();
            modelValue.put("evalCount", evalCount);
            modelValue.put("avgScore", avgScore);
            modelValue.put("passRate", evalCount > 0 ? round2(passCount * 100.0 / evalCount) : 0);
            res.put("modelValue", modelValue);

            // ---- 产出②：归档模型价值评分（成本/转化/风险分均值） ----
            List<Map<String, Object>> archives = evalArchiveMapper.listArchives();
            double costScore = 0, convScore = 0, riskScore = 0;
            long active = 0;
            Map<String, Long> gradeDist = new LinkedHashMap<>();
            for (Map<String, Object> a : archives) {
                if (a.get("revived_at") != null) continue;
                active++;
                costScore += toDouble(a.get("score_cost"));
                convScore += toDouble(a.get("score_conversion"));
                riskScore += toDouble(a.get("score_risk_acc"));
                String g = String.valueOf(a.get("value_score"));
                gradeDist.merge(g, 1L, Long::sum);
            }
            Map<String, Object> archiveValue = new LinkedHashMap<>();
            archiveValue.put("activeArchives", active);
            archiveValue.put("avgCostScore", active > 0 ? round2(costScore / active) : 0);
            archiveValue.put("avgConversionScore", active > 0 ? round2(convScore / active) : 0);
            archiveValue.put("avgRiskAccScore", active > 0 ? round2(riskScore / active) : 0);
            archiveValue.put("gradeDist", gradeDist);
            res.put("archiveValue", archiveValue);

            return res;
        }).flatMap(res -> appendSaving(res));
    }

    /** 产出③：优化建议落地节省（mas_platform_config/ADVICES 中已执行/已验证/已关闭的建议） */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> appendSaving(Map<String, Object> res) {
        return configService.getJson(ADVICES_KEY).map(raw -> {
            List<Map<String, Object>> advices = raw instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
            double monthlySaving = 0;
            long landed = 0;
            for (Map<String, Object> a : advices) {
                String status = String.valueOf(a.get("status"));
                if ("EXECUTED".equals(status) || "VERIFIED".equals(status) || "CLOSED".equals(status)) {
                    landed++;
                    monthlySaving += toDouble(a.get("estimatedSaving"));
                }
            }
            Map<String, Object> saving = new LinkedHashMap<>();
            saving.put("landedAdvices", landed);
            saving.put("monthlySaving", monthlySaving);
            saving.put("annualizedSaving", round2(monthlySaving * 12));
            res.put("saving", saving);

            // ---- 投入产出比：年化节省 / 本月投入（投入为 0 时不计算，返回 null 而非误导值） ----
            double invest = ((Map<String, Object>) res.get("investment")).get("monthCost") == null ? 0
                    : toDouble(((Map<String, Object>) res.get("investment")).get("monthCost"));
            res.put("roiRatio", invest > 0 ? round2(monthlySaving * 12 / invest) : null);
            return res;
        }).defaultIfEmpty(res);
    }

    // ---- helpers ----

    private static double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

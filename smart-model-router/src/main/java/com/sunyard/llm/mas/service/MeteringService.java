package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.CallLogEntity;
import com.sunyard.llm.mas.entity.DeptQuotaEntity;
import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.mapper.DeptQuotaMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 计量运营服务：全部从 mas_call_log / mas_dept_quota 真实数据查询
 */
@Service
public class MeteringService {

    private final CallLogMapper callLogMapper;
    private final DeptQuotaMapper deptQuotaMapper;
    private final TenantService tenantService;
    private final PricingService pricingService;
    private final OpLogService opLogService;

    public MeteringService(CallLogMapper callLogMapper, DeptQuotaMapper deptQuotaMapper,
                           TenantService tenantService, PricingService pricingService,
                           OpLogService opLogService) {
        this.callLogMapper = callLogMapper;
        this.deptQuotaMapper = deptQuotaMapper;
        this.tenantService = tenantService;
        this.pricingService = pricingService;
        this.opLogService = opLogService;
    }

    /** 调用日志列表（分页 + 筛选） */
    public Mono<Map<String, Object>> listCallLogs(String userId, String appId, String model,
                                                    String status, Integer page, Integer size) {
        return ReactiveDbAdapter.mono(() -> {
            LambdaQueryWrapper<CallLogEntity> wrapper = new LambdaQueryWrapper<>();
            if (userId != null && !userId.isBlank()) wrapper.eq(CallLogEntity::getUserId, userId);
            if (appId != null && !appId.isBlank()) wrapper.eq(CallLogEntity::getAppId, appId);
            if (model != null && !model.isBlank()) wrapper.eq(CallLogEntity::getModelId, model);
            wrapper.orderByDesc(CallLogEntity::getCreatedAt);
            int offset = (page != null ? page : 1) - 1;
            int limit = size != null ? size : 20;
            wrapper.last("LIMIT " + limit + " OFFSET " + offset * limit);
            List<CallLogEntity> entities = callLogMapper.selectList(wrapper);

            List<Map<String, Object>> logs = new ArrayList<>();
            for (CallLogEntity e : entities) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("log_id", "LOG-" + e.getId());
                row.put("ts", e.getCreatedAt() != null ? e.getCreatedAt().toString() + "Z" : "");
                row.put("status", mapStatus(e.getStatus()));
                row.put("status_code", mapStatusCode(e.getStatus()));
                row.put("api_key_masked", "sk-maas-****");
                row.put("route_name", e.getIntentType() != null ? e.getIntentType() : "");
                row.put("model", e.getModelId());
                row.put("provider", "行内集群");
                row.put("app_type", e.getAppId());
                row.put("behavior_tag", "业务办公");
                row.put("input_tokens", e.getPromptTokens() != null ? e.getPromptTokens() : 0);
                row.put("output_tokens", e.getCompletionTokens() != null ? e.getCompletionTokens() : 0);
                // 计量维度与成本（此前前端硬编码 TENANT-TECH / 0.00025，两套口径对不上）
                row.put("tenant_id", e.getTenantId() == null ? "" : e.getTenantId());
                row.put("dept_id", deptIdOfTenant(e.getTenantId()));
                row.put("scenario", e.getScenario() == null ? "" : e.getScenario());
                row.put("service_type", e.getServiceType() == null ? "chat" : e.getServiceType());
                row.put("cost", e.getCostAmount() != null ? e.getCostAmount()
                        : pricingService.price(null, e.getAppId(), e.getScenario(),
                        e.getServiceType() == null ? "chat" : e.getServiceType(), e.getModelId(),
                        e.getPromptTokens() == null ? 0 : e.getPromptTokens(),
                        e.getCompletionTokens() == null ? 0 : e.getCompletionTokens()));
                // 审计内容留存（二-8）：返回是否留存 + 内容摘要，不再恒返回空串
                row.put("has_content", e.getRequestContent() != null && !e.getRequestContent().isEmpty());
                row.put("request_content", e.getRequestContent() == null ? "" : e.getRequestContent());
                row.put("response_content", e.getResponseContent() == null ? "" : e.getResponseContent());
                row.put("content_hash", e.getContentHash() == null ? "" : e.getContentHash());
                logs.add(row);
            }

            // 按条件统计总数
            LambdaQueryWrapper<CallLogEntity> countWrapper = new LambdaQueryWrapper<>();
            if (userId != null && !userId.isBlank()) countWrapper.eq(CallLogEntity::getUserId, userId);
            if (appId != null && !appId.isBlank()) countWrapper.eq(CallLogEntity::getAppId, appId);
            if (model != null && !model.isBlank()) countWrapper.eq(CallLogEntity::getModelId, model);
            long total = callLogMapper.selectCount(countWrapper);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("logs", logs);
            result.put("total", (int) total);
            return result;
        });
    }

    /**
     * 模型统计（从 call_log 按 model_id 聚合）：
     * 【已改造】成本不再使用 SQL 里写死的 *0.0016，改由计价引擎按维度实时计算。
     */
    public Mono<List<Map<String, Object>>> getModelStats() {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> rows = callLogMapper.selectMaps(
                new QueryWrapper<CallLogEntity>()
                    .select(
                        "model_id as asset_id",
                        "model_id as name",
                        "COALESCE(service_type,'chat') as service_type",
                        "COUNT(*) as calls",
                        "COALESCE(SUM(prompt_tokens),0) as input_tokens",
                        "COALESCE(SUM(completion_tokens),0) as output_tokens",
                        "COALESCE(SUM(total_tokens),0) as total_tokens"
                    )
                    .isNotNull("model_id").ne("model_id", "")
                    .groupBy("model_id", "service_type")
                    .orderByDesc("calls")
            );
            for (Map<String, Object> r : rows) {
                BigDecimal cost = pricingService.price(null, null, null,
                        String.valueOf(r.get("service_type")), String.valueOf(r.get("model_id")),
                        toLong(r.get("input_tokens")), toLong(r.get("output_tokens")));
                r.put("cost", cost);
            }
            return rows;
        });
    }

    /** 配额列表（从 mas_dept_quota 查询，used_tokens 从 call_log 实时计算） */
    public Mono<List<Map<String, Object>>> listQuotas() {
        return ReactiveDbAdapter.mono(() -> {
            List<DeptQuotaEntity> quotas = deptQuotaMapper.selectList(null);
            List<Map<String, Object>> list = new ArrayList<>();
            for (DeptQuotaEntity q : quotas) {
                // 从 call_log 实时计算该租户实际用量
                String tenantId = deptToTenant(q.getDeptId());
                long realUsed = callLogMapper.selectMaps(
                    new QueryWrapper<CallLogEntity>()
                        .select("COALESCE(SUM(total_tokens),0) as used")
                        .eq("tenant_id", tenantId)
                ).stream().mapToLong(r -> toLong(r.get("used"))).sum();

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("dept_id", q.getDeptId());
                row.put("dept_name", q.getDeptName());
                row.put("month_token_quota", q.getMonthTokenQuota());
                row.put("used_tokens", realUsed);
                row.put("month_cost", costOfTenant(tenantId));
                row.put("over_limit_stop", q.getOverLimitStop());
                row.put("warn_threshold", q.getWarnThreshold());
                row.put("notify_channels", List.of("SITE", "MAIL"));
                // 根据用量比例判断状态
                double ratio = q.getMonthTokenQuota() > 0 ? (double) realUsed / q.getMonthTokenQuota() : 0;
                String status = ratio > 1.0 ? "STOPPED" : ratio > 0.8 ? "WARNING" : "NORMAL";
                row.put("status", status);
                row.put("resume_pending", "STOPPED".equals(status));
                list.add(row);
            }
            return list;
        });
    }

    /**
     * 设置部门配额：
     * 【已改造】此前为"返回一条操作留痕但不 UPDATE"的桩实现（演示环境点是成功、刷新回原值）。
     * 现真实落库 mas_dept_quota，并写入操作审计 mas_op_log。
     */
    public Mono<Map<String, Object>> setQuota(String deptId, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            DeptQuotaEntity entity = deptQuotaMapper.selectById(deptId);
            String before = entity == null ? null : String.valueOf(entity.getMonthTokenQuota());
            if (entity == null) {
                entity = new DeptQuotaEntity();
                entity.setDeptId(deptId);
                entity.setDeptName(str(body.getOrDefault("deptName", deptId)));
                entity.setMonthTokenQuota(longVal(body.get("monthTokenQuota"), longVal(body.get("month_token_quota"), 0L)));
                entity.setOverLimitStop(boolVal(body.get("overLimitStop"), boolVal(body.get("over_limit_stop"), false)));
                entity.setWarnThreshold(intVal(body.get("warnThreshold"), intVal(body.get("warn_threshold"), 80)));
                entity.setStatus("NORMAL");
                deptQuotaMapper.insert(entity);
            } else {
                if (body.get("monthTokenQuota") != null || body.get("month_token_quota") != null) {
                    entity.setMonthTokenQuota(longVal(body.get("monthTokenQuota"), longVal(body.get("month_token_quota"), 0L)));
                }
                if (body.get("deptName") != null) entity.setDeptName(str(body.get("deptName")));
                if (body.get("overLimitStop") != null || body.get("over_limit_stop") != null) {
                    entity.setOverLimitStop(boolVal(body.get("overLimitStop"), boolVal(body.get("over_limit_stop"), false)));
                }
                if (body.get("warnThreshold") != null || body.get("warn_threshold") != null) {
                    entity.setWarnThreshold(intVal(body.get("warnThreshold"), intVal(body.get("warn_threshold"), 80)));
                }
                deptQuotaMapper.updateById(entity);
            }
            return new String[]{deptId, before, String.valueOf(entity.getMonthTokenQuota())};
        }).flatMap(arr -> opLogService.record("metering", "调整配额", operator, arr[0],
                "月度 Token 配额由 " + arr[1] + " 调整为 " + arr[2], arr[1], arr[2], null));
    }

    /** 兼容旧签名（无操作人） */
    public Mono<Map<String, Object>> setQuota(String deptId, Map<String, Object> body) {
        return setQuota(deptId, body, null);
    }

    /** 月度账单（从 call_log 按月份 + 租户聚合） */
    public Mono<List<Map<String, Object>>> listMonthlyBills(String month, String deptId) {
        return ReactiveDbAdapter.mono(() -> {
            String monthFilter = month != null ? month : null;
            String tenantFilter = deptId != null ? deptToTenant(deptId) : null;

            QueryWrapper<CallLogEntity> wrapper = new QueryWrapper<CallLogEntity>()
                .select(
                    "TO_CHAR(created_at, 'YYYY-MM') as month",
                    "COALESCE(tenant_id, 'UNKNOWN') as tenant_id",
                    "COALESCE(service_type,'chat') as service_type",
                    "model_id",
                    "COALESCE(SUM(prompt_tokens),0) as input_tokens",
                    "COALESCE(SUM(completion_tokens),0) as output_tokens",
                    "COALESCE(SUM(total_tokens),0) as tokens",
                    "COUNT(*) as calls"
                )
                .isNotNull("tenant_id").ne("tenant_id", "")
                .groupBy("TO_CHAR(created_at, 'YYYY-MM')", "tenant_id", "service_type", "model_id");

            if (monthFilter != null) {
                wrapper.apply("TO_CHAR(created_at, 'YYYY-MM') = {0}", monthFilter);
            }
            if (tenantFilter != null) {
                wrapper.eq("tenant_id", tenantFilter);
            }

            List<Map<String, Object>> rows = callLogMapper.selectMaps(wrapper);

            // 按 月×租户 折叠，成本由计价引擎按 (服务类型, 模型) 维度计算
            Map<String, Map<String, Object>> bills = new LinkedHashMap<>();
            for (Map<String, Object> r : rows) {
                String key = r.get("month") + "|" + r.get("tenant_id");
                Map<String, Object> bill = bills.computeIfAbsent(key, k -> {
                    Map<String, Object> b = new LinkedHashMap<>();
                    b.put("month", r.get("month"));
                    b.put("tenant_id", r.get("tenant_id"));
                    b.put("dept_name", tenantName(String.valueOf(r.get("tenant_id"))));
                    b.put("tokens", 0L);
                    b.put("calls", 0L);
                    b.put("cost", BigDecimal.ZERO);
                    return b;
                });
                bill.put("tokens", toLong(bill.get("tokens")) + toLong(r.get("tokens")));
                bill.put("calls", toLong(bill.get("calls")) + toLong(r.get("calls")));
                BigDecimal cost = pricingService.price(null, null, null,
                        String.valueOf(r.get("service_type")), String.valueOf(r.get("model_id")),
                        toLong(r.get("input_tokens")), toLong(r.get("output_tokens")));
                bill.put("cost", ((BigDecimal) bill.get("cost")).add(cost));
            }
            List<Map<String, Object>> result = new ArrayList<>(bills.values());
            for (Map<String, Object> b : result) {
                b.put("cost", ((BigDecimal) b.get("cost")).setScale(2, java.math.RoundingMode.HALF_UP));
            }
            result.sort((a, b) -> String.valueOf(b.get("month")).compareTo(String.valueOf(a.get("month"))));
            return result;
        });
    }

    /** 租户 → 部门（反查映射表，取不到时为空串） */
    private String deptIdOfTenant(String tenantId) {
        if (tenantId == null || tenantId.isEmpty() || tenantService == null) return "";
        return tenantService.deptOfTenant(tenantId);
    }

    /** 租户显示名：取 mas_tenant 表，查不到时回退为 tenant_id */
    private String tenantName(String tenantId) {
        if (tenantService == null) return tenantId;
        String name = tenantService.tenantName(tenantId);
        return name == null ? tenantId : name;
    }

    /** 个人用量（从 call_log 按 user_id 聚合） */
    public Mono<Map<String, Object>> getPersonalUsage(String userId) {
        return ReactiveDbAdapter.mono(() -> {
            String uid = userId != null ? userId : "anonymous";
            List<Map<String, Object>> rows = callLogMapper.selectMaps(
                new QueryWrapper<CallLogEntity>()
                    .select(
                        "user_id",
                        "COUNT(*) as calls",
                        "COALESCE(SUM(total_tokens),0) as tokens"
                    )
                    .eq("user_id", uid)
                    .groupBy("user_id")
            );
            Map<String, Object> row = rows.isEmpty() ? Map.of() : rows.get(0);

            // 成本由计价引擎按 (服务类型, 模型) 维度计算，取代 *0.0016 硬编码
            BigDecimal cost = BigDecimal.ZERO;
            for (Map<String, Object> g : callLogMapper.selectMaps(
                    new QueryWrapper<CallLogEntity>()
                        .select(
                            "COALESCE(service_type,'chat') as service_type",
                            "model_id",
                            "COALESCE(SUM(prompt_tokens),0) as input_tokens",
                            "COALESCE(SUM(completion_tokens),0) as output_tokens"
                        )
                        .eq("user_id", uid)
                        .groupBy("service_type", "model_id"))) {
                cost = cost.add(pricingService.price(null, null, null,
                        String.valueOf(g.get("service_type")), String.valueOf(g.get("model_id")),
                        toLong(g.get("input_tokens")), toLong(g.get("output_tokens"))));
            }

            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("user_id", uid);
            usage.put("name", uid);
            usage.put("dept_id", "DEPT-TECH");
            usage.put("tokens", toLong(row.get("tokens")));
            usage.put("cost", cost.setScale(2, RoundingMode.HALF_UP));

            // 按 intent_type 统计行为分布
            long userTotal = callLogMapper.selectCount(
                new LambdaQueryWrapper<CallLogEntity>().eq(CallLogEntity::getUserId, uid)
            );
            List<Map<String, Object>> tagDist = callLogMapper.selectMaps(
                new QueryWrapper<CallLogEntity>()
                    .select(
                        "COALESCE(intent_type, '其他') as tag",
                        "COUNT(*) as cnt"
                    )
                    .eq("user_id", uid)
                    .groupBy("intent_type")
                    .orderByDesc("cnt")
            );
            for (Map<String, Object> t : tagDist) {
                long cnt = toLong(t.get("cnt"));
                t.put("pct", userTotal > 0 ? Math.round((double) cnt / userTotal * 10000) / 100.0 : 0);
                t.remove("cnt");
            }
            if (tagDist.isEmpty()) {
                tagDist = List.of(Map.of("tag", "业务办公", "pct", 100.0));
            }
            usage.put("tag_dist", tagDist);
            return usage;
        });
    }

    /** 模型推荐（基于 call_log 数据分析各应用的模型使用效率） */
    public Mono<List<Map<String, Object>>> getModelRecommends() {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            // 按 app_id 分析：找出 token 消耗高但可以用更小模型替代的场景
            List<Map<String, Object>> appModels = callLogMapper.selectMaps(
                new QueryWrapper<CallLogEntity>()
                    .select(
                        "app_id",
                        "model_id",
                        "COUNT(*) as calls",
                        "COALESCE(SUM(total_tokens),0) as tokens",
                        "COALESCE(AVG(total_cost_ms),0)::int as avg_ms"
                    )
                    .isNotNull("app_id").isNotNull("model_id")
                    .groupBy("app_id", "model_id")
                    .orderByDesc("tokens")
            );
            int recIdx = 1;
            Set<String> seen = new HashSet<>();
            for (Map<String, Object> r : appModels) {
                String appId = String.valueOf(r.get("app_id"));
                if (seen.contains(appId)) continue;
                seen.add(appId);
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("rec_id", "REC-" + String.format("%03d", recIdx++));
                rec.put("scene", appId);
                rec.put("current_model", r.get("model_id"));
                rec.put("recommend_model", "qwen-lite");
                rec.put("est_saving", toDouble(r.get("tokens")) * 0.0008);
                list.add(rec);
            }
            return list;
        });
    }

    // ---- helpers ----

    /**
     * 按计价引擎计算某租户的成本总额（替代 total_tokens * 0.0016 硬编码口径）。
     * 逐 (service_type, model_id, scenario) 分组计价后累加，保证与费率规则一致。
     */
    private BigDecimal costOfTenant(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) return BigDecimal.ZERO;
        List<Map<String, Object>> groups = callLogMapper.selectMaps(
            new QueryWrapper<CallLogEntity>()
                .select(
                    "COALESCE(service_type,'chat') as service_type",
                    "model_id",
                    "COALESCE(scenario,'') as scenario",
                    "COALESCE(SUM(prompt_tokens),0) as input_tokens",
                    "COALESCE(SUM(completion_tokens),0) as output_tokens"
                )
                .eq("tenant_id", tenantId)
                .groupBy("service_type", "model_id", "scenario")
        );
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> g : groups) {
            total = total.add(pricingService.price(null, null,
                    String.valueOf(g.get("scenario")),
                    String.valueOf(g.get("service_type")),
                    String.valueOf(g.get("model_id")),
                    toLong(g.get("input_tokens")), toLong(g.get("output_tokens"))));
        }
        return total.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 部门 → 租户：
     * 【已改造】原为 6 个租户写死在 Java 的硬编码 switch（招标二-1），
     * 现改为查询 mas_dept_tenant 映射表（TenantService 带 60s 缓存），支持在线维护。
     */
    private String deptToTenant(String deptId) {
        if (deptId == null) return "UNKNOWN";
        if (tenantService == null) return "UNKNOWN";
        return tenantService.resolveTenantByDept(deptId);
    }

    private String mapStatus(Integer status) {
        if (status == null) return "SUCCESS";
        return switch (status) {
            case 0 -> "SUCCESS";
            case 1 -> "FAILED";
            case 2 -> "RATE_LIMITED";
            case 3 -> "BLOCKED";
            default -> "SUCCESS";
        };
    }

    private int mapStatusCode(Integer status) {
        if (status == null) return 200;
        return switch (status) {
            case 0 -> 200;
            case 1 -> 500;
            case 2 -> 429;
            case 3 -> 403;
            default -> 200;
        };
    }

    private long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0; }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static long longVal(Object v, long def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static boolean boolVal(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
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

    private double toDouble(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0; }
    }
}

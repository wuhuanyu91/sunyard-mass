package com.sunyard.llm.mas.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunyard.llm.mas.mapper.PricingMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 差异化计价引擎（公告一-4「使用量—资源消耗—成本」映射与计量计价 + 一-5 差异化计量规则）：
 * <p>
 * 支持五个维度（任一维度留空即通配，命中即停，priority 越大越优先）：
 * 部门 dept_id / 系统 app_id / 业务场景 scenario / 服务类型 service_type + 模型 model_id / 使用时段 time_start~time_end
 * <p>
 * 取代原先 9 处硬编码 {@code total_tokens * 0.0016}。
 */
@Service
public class PricingService {

    private static final Logger log = LoggerFactory.getLogger(PricingService.class);
    private static final String MODULE = "pricing";
    /** 兜底单价（元/token）：无任何规则命中时使用，与改造前口径一致 */
    private static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal("0.0016");

    private final PricingMapper pricingMapper;
    private final OpLogService opLogService;

    /** 规则缓存 30s：请求链路高频调用 */
    private final Cache<String, List<Map<String, Object>>> ruleCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(10).build();

    public PricingService(PricingMapper pricingMapper, OpLogService opLogService) {
        this.pricingMapper = pricingMapper;
        this.opLogService = opLogService;
    }

    // ---------------- 规则管理 ----------------

    public Mono<List<Map<String, Object>>> listRules() {
        return ReactiveDbAdapter.mono(pricingMapper::listRules);
    }

    public Mono<Map<String, Object>> createRule(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String ruleCode = str(body.get("ruleCode"));
            if (ruleCode.isEmpty()) throw new IllegalArgumentException("ruleCode 必填");
            pricingMapper.insertRule(ruleCode, str(body.getOrDefault("ruleName", ruleCode)),
                    nullableStr(body.get("deptId")), nullableStr(body.get("appId")),
                    nullableStr(body.get("scenario")), nullableStr(body.get("serviceType")),
                    nullableStr(body.get("modelId")),
                    nullableStr(body.get("timeStart")), nullableStr(body.get("timeEnd")),
                    dec(body.get("inputPrice")), dec(body.get("outputPrice")), dec(body.get("requestPrice")),
                    intVal(body.get("priority"), 0), intVal(body.get("status"), 1),
                    null, null, operator);
            invalidate();
            return ruleCode;
        }).flatMap(code -> opLogService.record(MODULE, "新增计价规则", operator, code, "创建费率规则 " + code));
    }

    public Mono<Map<String, Object>> updateRule(String ruleCode, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            pricingMapper.updateRule(ruleCode, str(body.get("ruleName")),
                    nullableStr(body.get("deptId")), nullableStr(body.get("appId")),
                    nullableStr(body.get("scenario")), nullableStr(body.get("serviceType")),
                    nullableStr(body.get("modelId")),
                    nullableStr(body.get("timeStart")), nullableStr(body.get("timeEnd")),
                    dec(body.get("inputPrice")), dec(body.get("outputPrice")), dec(body.get("requestPrice")),
                    intVal(body.get("priority"), 0), intVal(body.get("status"), 1), null, null);
            invalidate();
            return ruleCode;
        }).flatMap(code -> opLogService.record(MODULE, "修改计价规则", operator, code, "更新费率规则"));
    }

    public Mono<Map<String, Object>> deleteRule(String ruleCode, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            pricingMapper.deleteRule(ruleCode);
            invalidate();
            return ruleCode;
        }).flatMap(code -> opLogService.record(MODULE, "删除计价规则", operator, code, "删除费率规则 " + code));
    }

    // ---------------- 计价核心 ----------------

    /**
     * 单笔计价：按五维匹配最优费率规则并计算金额。
     *
     * @return 金额（元），scale=6
     */
    public BigDecimal price(String deptId, String appId, String scenario, String serviceType,
                            String modelId, LocalDateTime at,
                            long promptTokens, long completionTokens) {
        Map<String, Object> rule = matchRule(deptId, appId, scenario, serviceType, modelId, at);
        BigDecimal in;
        BigDecimal out;
        BigDecimal perCall;
        if (rule == null) {
            in = DEFAULT_UNIT_PRICE;
            out = DEFAULT_UNIT_PRICE;
            perCall = BigDecimal.ZERO;
        } else {
            in = dec(rule.get("input_price"));
            out = dec(rule.get("output_price"));
            perCall = dec(rule.get("request_price"));
        }
        BigDecimal amount = in.multiply(BigDecimal.valueOf(promptTokens))
                .add(out.multiply(BigDecimal.valueOf(completionTokens)))
                .add(perCall);
        return amount.setScale(6, RoundingMode.HALF_UP);
    }

    /** 简化签名：按当前时间计价 */
    public BigDecimal price(String deptId, String appId, String scenario, String serviceType,
                            String modelId, long promptTokens, long completionTokens) {
        return price(deptId, appId, scenario, serviceType, modelId, LocalDateTime.now(),
                promptTokens, completionTokens);
    }

    /** 命中规则（供前端展示"本次命中哪条费率"） */
    public Map<String, Object> matchRule(String deptId, String appId, String scenario,
                                         String serviceType, String modelId, LocalDateTime at) {
        List<Map<String, Object>> rules = loadRules();
        LocalTime now = (at == null ? LocalDateTime.now() : at).toLocalTime();
        for (Map<String, Object> r : rules) {
            if (Integer.valueOf(1).equals(r.get("status")) == false) continue;
            if (!dimMatch(r.get("dept_id"), deptId)) continue;
            if (!dimMatch(r.get("app_id"), appId)) continue;
            if (!dimMatch(r.get("scenario"), scenario)) continue;
            if (!dimMatch(r.get("service_type"), serviceType)) continue;
            if (!dimMatch(r.get("model_id"), modelId)) continue;
            if (!timeMatch(r.get("time_start"), r.get("time_end"), now)) continue;
            return r;
        }
        return null;
    }

    /** 试算（前端费率试算器）：返回命中规则 + 金额 */
    public Mono<Map<String, Object>> simulate(Map<String, Object> body) {
        return ReactiveDbAdapter.mono(() -> {
            long promptTokens = longVal(body.get("promptTokens"));
            long completionTokens = longVal(body.get("completionTokens"));
            Map<String, Object> rule = matchRule(nullableStr(body.get("deptId")), nullableStr(body.get("appId")),
                    nullableStr(body.get("scenario")), nullableStr(body.get("serviceType")),
                    nullableStr(body.get("modelId")), LocalDateTime.now());
            BigDecimal amount = price(nullableStr(body.get("deptId")), nullableStr(body.get("appId")),
                    nullableStr(body.get("scenario")), nullableStr(body.get("serviceType")),
                    nullableStr(body.get("modelId")), promptTokens, completionTokens);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("amount", amount);
            result.put("prompt_tokens", promptTokens);
            result.put("completion_tokens", completionTokens);
            result.put("rule_code", rule == null ? "DEFAULT" : rule.get("rule_code"));
            result.put("rule_name", rule == null ? "默认费率(0.0016 元/token)" : rule.get("rule_name"));
            return result;
        });
    }

    // ---------------- internals ----------------

    private List<Map<String, Object>> loadRules() {
        try {
            return ruleCache.get("all", k -> {
                try {
                    return new ArrayList<>(pricingMapper.listRules());
                } catch (Exception e) {
                    log.warn("pricing rules load failed, fallback to default", e);
                    return new ArrayList<>();
                }
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private static boolean dimMatch(Object ruleDim, Object actual) {
        if (ruleDim == null || String.valueOf(ruleDim).isEmpty()) return true;  // 通配
        if (actual == null || String.valueOf(actual).isEmpty()) return false;
        return String.valueOf(ruleDim).equalsIgnoreCase(String.valueOf(actual));
    }

    /** 时段匹配：支持跨夜（如 22:00~06:00） */
    private static boolean timeMatch(Object start, Object end, LocalTime now) {
        if (start == null || end == null) return true;
        String s = String.valueOf(start);
        String e = String.valueOf(end);
        if (s.isEmpty() || e.isEmpty()) return true;
        try {
            LocalTime st = LocalTime.parse(s);
            LocalTime et = LocalTime.parse(e);
            if (st.isBefore(et) || st.equals(et)) {
                return !now.isBefore(st) && now.isBefore(et);
            }
            // 跨夜
            return !now.isBefore(st) || now.isBefore(et);
        } catch (Exception ex) {
            return true;
        }
    }

    public void invalidate() {
        ruleCache.invalidateAll();
    }

    // ---------------- helpers ----------------

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String nullableStr(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isEmpty() ? null : s;
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

    private static long longVal(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private static BigDecimal dec(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal b) return b;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try {
            return new BigDecimal(v.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

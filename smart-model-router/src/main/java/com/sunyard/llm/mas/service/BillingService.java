package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.BillingMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 计费结算与对账服务（公告一-4 "计量采集、计费结算与对账业务"）：
 * 账单生成 → 确认 → 锁账（账期封闭，不可再变）→ 结算；并提供平台口径 vs 上游口径对账。
 * 说明：月度账单不再是"对 call_log 的即时聚合查询"，而是有快照、有账期、可锁账的账单实体。
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final String MODULE = "billing";
    private static final BigDecimal DIFF_TOLERANCE = new BigDecimal("0.01"); // 差异容忍 ±0.01 元

    private final BillingMapper billingMapper;
    private final OpLogService opLogService;

    public BillingService(BillingMapper billingMapper, OpLogService opLogService) {
        this.billingMapper = billingMapper;
        this.opLogService = opLogService;
    }

    /** 生成账期账单（可为全部租户；已锁定账期不覆盖） */
    public Mono<Map<String, Object>> generateBills(String month, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String billMonth = month != null ? month : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
            List<Map<String, Object>> rows = billingMapper.aggregateByTenant(billMonth);
            int created = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (Map<String, Object> r : rows) {
                String tenantId = String.valueOf(r.get("tenant_id"));
                long calls = toLong(r.get("total_calls"));
                long tokens = toLong(r.get("total_tokens"));
                BigDecimal amount = toDec(r.get("total_amount")).setScale(2, RoundingMode.HALF_UP);
                String billNo = "BILL-" + billMonth.replace("-", "") + "-" + tenantId;
                billingMapper.upsertBill(billNo, billMonth, tenantId, null, calls, tokens, amount);
                // 明细快照
                List<Map<String, Object>> items = billingMapper.aggregateItems(billMonth, tenantId);
                for (Map<String, Object> it : items) {
                    billingMapper.insertBillItem(billNo,
                            String.valueOf(it.get("app_id")), String.valueOf(it.get("model_id")),
                            String.valueOf(it.get("scenario")), String.valueOf(it.get("service_type")),
                            toLong(it.get("calls")), toLong(it.get("input_tokens")),
                            toLong(it.get("output_tokens")),
                            toDec(it.get("amount")).setScale(2, RoundingMode.HALF_UP));
                }
                created++;
                total = total.add(amount);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bill_month", billMonth);
            result.put("bill_count", created);
            result.put("total_amount", total.setScale(2, RoundingMode.HALF_UP));
            log.info("bills generated: month={}, count={}, total={}", billMonth, created, total);
            return result;
        }).flatMap(res -> opLogService.record(MODULE, "生成账单", operator, String.valueOf(res.get("bill_month")),
                "生成账单 " + res.get("bill_count") + " 张，合计 " + res.get("total_amount") + " 元"));
    }

    public Mono<List<Map<String, Object>>> listBills(String month, String tenantId) {
        return ReactiveDbAdapter.mono(() -> billingMapper.listBills(month, tenantId));
    }

    public Mono<List<Map<String, Object>>> listBillItems(String billNo) {
        return ReactiveDbAdapter.mono(() -> billingMapper.listBillItems(billNo));
    }

    /** 确认账单（DRAFT → CONFIRMED） */
    public Mono<Map<String, Object>> confirmBill(String billNo, String operator) {
        return setStatus(billNo, "CONFIRMED", operator, "确认账单");
    }

    /** 锁账：账期封闭，标记该账期调用记录已入账，此后聚合不再变动（银行财务硬要求） */
    public Mono<Map<String, Object>> lockBill(String billNo, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            Map<String, Object> bill = findBill(billNo);
            if (bill == null) throw new IllegalArgumentException("账单不存在：" + billNo);
            String status = String.valueOf(bill.get("status"));
            if ("LOCKED".equals(status) || "SETTLED".equals(status)) {
                throw new IllegalStateException("账单已锁定，不可重复操作：" + billNo);
            }
            billingMapper.updateBillStatus(billNo, "LOCKED", operator);
            billingMapper.markBilled(String.valueOf(bill.get("bill_month")),
                    String.valueOf(bill.get("tenant_id")));
            log.info("bill locked: {}, month={}", billNo, bill.get("bill_month"));
            return billNo;
        }).flatMap(no -> opLogService.record(MODULE, "锁账", operator, no, "账期封闭，标记已入账"));
    }

    /** 结算（LOCKED → SETTLED） */
    public Mono<Map<String, Object>> settleBill(String billNo, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            Map<String, Object> bill = findBill(billNo);
            if (bill == null) throw new IllegalArgumentException("账单不存在：" + billNo);
            if (!"LOCKED".equals(String.valueOf(bill.get("status")))) {
                throw new IllegalStateException("仅已锁定账单可结算：" + billNo);
            }
            billingMapper.updateBillStatus(billNo, "SETTLED", operator);
            return billNo;
        }).flatMap(no -> opLogService.record(MODULE, "结算", operator, no, "账单结算"));
    }

    // ---------------- 对账 ----------------

    /**
     * 对账：平台计量口径 vs 上游（行内财务/渠道系统）口径。
     * 差异 ≤0.01 元判 MATCHED，否则 DIFF。
     */
    public Mono<Map<String, Object>> reconcile(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String billMonth = str(body.get("billMonth"));
            String tenantId = str(body.get("tenantId"));
            BigDecimal platform = toDec(body.get("platformAmount"));
            BigDecimal upstream = toDec(body.get("upstreamAmount"));
            if (platform.compareTo(BigDecimal.ZERO) == 0) {
                // 未提供平台口径时，以账单金额为准
                List<Map<String, Object>> bills = billingMapper.listBills(billMonth, tenantId);
                platform = bills.stream().map(b -> toDec(b.get("total_amount")))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            BigDecimal diff = platform.subtract(upstream).abs().setScale(2, RoundingMode.HALF_UP);
            BigDecimal ratio = platform.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                    : diff.divide(platform, 6, RoundingMode.HALF_UP);
            String result = diff.compareTo(DIFF_TOLERANCE) <= 0 ? "MATCHED" : "DIFF";
            billingMapper.insertReconciliation(billMonth, tenantId, platform, upstream, diff, ratio, result,
                    str(body.get("remark")), operator);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("bill_month", billMonth);
            res.put("tenant_id", tenantId);
            res.put("platform_amount", platform);
            res.put("upstream_amount", upstream);
            res.put("diff_amount", diff);
            res.put("diff_ratio", ratio);
            res.put("result", result);
            return res;
        }).flatMap(res -> opLogService.record(MODULE, "账单对账", operator,
                str(res.get("tenant_id")), "对账结果 " + res.get("result")));
    }

    public Mono<List<Map<String, Object>>> listReconciliations(String month, String tenantId) {
        return ReactiveDbAdapter.mono(() -> billingMapper.listReconciliations(month, tenantId));
    }

    // ---------------- internals ----------------

    private Mono<Map<String, Object>> setStatus(String billNo, String status, String operator, String opType) {
        return ReactiveDbAdapter.mono(() -> {
            if (findBill(billNo) == null) throw new IllegalArgumentException("账单不存在：" + billNo);
            billingMapper.updateBillStatus(billNo, status, operator);
            return billNo;
        }).flatMap(no -> opLogService.record(MODULE, opType, operator, no, opType + " " + no));
    }

    private Map<String, Object> findBill(String billNo) {
        List<Map<String, Object>> all = billingMapper.listBills(null, null);
        for (Map<String, Object> b : all) {
            if (billNo.equals(String.valueOf(b.get("bill_no")))) return b;
        }
        return null;
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
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

    private static BigDecimal toDec(Object v) {
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

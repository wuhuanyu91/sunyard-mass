package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.BillingService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 计费结算与对账端点（公告一-4 技术要求点名"计量采集、计费结算与对账业务"）
 */
@RestController
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/internal/billing/generate")
    public Mono<Map<String, Object>> generate(
            @RequestParam(value = "month", required = false) String month,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return billingService.generateBills(month, operator);
    }

    @GetMapping("/internal/billing/bills")
    public Mono<List<Map<String, Object>>> listBills(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "tenant_id", required = false) String tenantId) {
        return billingService.listBills(month, tenantId);
    }

    @GetMapping("/internal/billing/bills/{billNo}/items")
    public Mono<List<Map<String, Object>>> listBillItems(@PathVariable("billNo") String billNo) {
        return billingService.listBillItems(billNo);
    }

    @PostMapping("/internal/billing/bills/{billNo}/confirm")
    public Mono<Map<String, Object>> confirm(@PathVariable("billNo") String billNo,
                                             @RequestHeader(value = "X-Operator", required = false) String operator) {
        return billingService.confirmBill(billNo, operator);
    }

    /** 锁账：账期封闭 */
    @PostMapping("/internal/billing/bills/{billNo}/lock")
    public Mono<Map<String, Object>> lock(@PathVariable("billNo") String billNo,
                                          @RequestHeader(value = "X-Operator", required = false) String operator) {
        return billingService.lockBill(billNo, operator);
    }

    @PostMapping("/internal/billing/bills/{billNo}/settle")
    public Mono<Map<String, Object>> settle(@PathVariable("billNo") String billNo,
                                            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return billingService.settleBill(billNo, operator);
    }

    @PostMapping("/internal/billing/reconciliations")
    public Mono<Map<String, Object>> reconcile(@RequestBody Map<String, Object> body,
                                               @RequestHeader(value = "X-Operator", required = false) String operator) {
        return billingService.reconcile(body, operator);
    }

    @GetMapping("/internal/billing/reconciliations")
    public Mono<List<Map<String, Object>>> listReconciliations(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "tenant_id", required = false) String tenantId) {
        return billingService.listReconciliations(month, tenantId);
    }
}

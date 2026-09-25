package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.PricingService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 差异化计价端点（公告一-4/一-5）：五维费率规则管理 + 费率试算
 */
@RestController
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/internal/pricing/rules")
    public Mono<List<Map<String, Object>>> listRules() {
        return pricingService.listRules();
    }

    @PostMapping("/internal/pricing/rules")
    public Mono<Map<String, Object>> createRule(@RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return pricingService.createRule(body, operator);
    }

    @PutMapping("/internal/pricing/rules/{ruleCode}")
    public Mono<Map<String, Object>> updateRule(@PathVariable("ruleCode") String ruleCode,
                                                @RequestBody Map<String, Object> body,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return pricingService.updateRule(ruleCode, body, operator);
    }

    @DeleteMapping("/internal/pricing/rules/{ruleCode}")
    public Mono<Map<String, Object>> deleteRule(@PathVariable("ruleCode") String ruleCode,
                                                @RequestHeader(value = "X-Operator", required = false) String operator) {
        return pricingService.deleteRule(ruleCode, operator);
    }

    /** 费率试算：给定维度与 token 量，返回命中规则与金额 */
    @PostMapping("/internal/pricing/simulate")
    public Mono<Map<String, Object>> simulate(@RequestBody Map<String, Object> body) {
        return pricingService.simulate(body);
    }
}

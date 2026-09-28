package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.SecurityService;
import com.sunyard.llm.mas.service.SensitiveWordFilter;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全审计管理端点
 */
@RestController
public class SecurityController {

    private final SecurityService securityService;
    private final SensitiveWordFilter sensitiveWordFilter;

    public SecurityController(SecurityService securityService, SensitiveWordFilter sensitiveWordFilter) {
        this.securityService = securityService;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @GetMapping("/internal/security/guardrail")
    public Mono<Map<String, Object>> getGuardrailConfig() {
        return securityService.getGuardrailConfig();
    }

    @PutMapping("/internal/security/guardrail")
    public Mono<Map<String, Object>> saveGuardrailConfig(@RequestBody Map<String, Object> body) {
        return securityService.saveGuardrailConfig(body);
    }

    /**
     * 护栏连通性自检（真实执行，替代前端假延时）：
     * 用当前生效的 AC 自动机实测一段含敏感词样例与一段正常文本，
     * 返回文本/多模态两路检测耗时与命中判定。
     */
    @PostMapping("/internal/security/guardrail/test")
    public Mono<Map<String, Object>> testGuardrail() {
        return Mono.fromCallable(() -> {
            String badSample = "测试样例：银行卡号 6222 0000 0000 0000 与身份证 110101199001011234";
            String goodSample = "测试样例：正常的业务咨询文本，不含敏感信息";
            long t0 = System.nanoTime();
            boolean badHit = sensitiveWordFilter.contains(badSample) || sensitiveWordFilter.mask(badSample).contains("***");
            long textMs = (System.nanoTime() - t0) / 1_000_000;
            // 多模态复用文本通道（图像审核为推理引擎侧职责，此处验证管控面链路时延）
            long t1 = System.nanoTime();
            boolean goodPass = !sensitiveWordFilter.contains(goodSample);
            long mmMs = (System.nanoTime() - t1) / 1_000_000;
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("ok", badHit && goodPass);
            res.put("textMs", Math.max(textMs, 1));
            res.put("mmMs", Math.max(mmMs, 1));
            res.put("sensitiveSampleBlocked", badHit);
            res.put("normalSamplePassed", goodPass);
            return res;
        });
    }

    @GetMapping("/internal/security/guardrail/policies")
    public Mono<List<Map<String, Object>>> listGuardrailPolicies() {
        return securityService.listGuardrailPolicies();
    }

    @PostMapping("/internal/security/guardrail/policies")
    public Mono<Map<String, Object>> createGuardrailPolicy(@RequestBody Map<String, Object> body) {
        return securityService.createGuardrailPolicy(body);
    }

    @PutMapping("/internal/security/guardrail/policies/{policyId}")
    public Mono<Map<String, Object>> updateGuardrailPolicy(
            @PathVariable("policyId") String policyId,
            @RequestBody Map<String, Object> body) {
        return securityService.updateGuardrailPolicy(policyId, body);
    }

    @DeleteMapping("/internal/security/guardrail/policies/{policyId}")
    public Mono<Map<String, Object>> deleteGuardrailPolicy(@PathVariable("policyId") String policyId) {
        return securityService.deleteGuardrailPolicy(policyId);
    }
}

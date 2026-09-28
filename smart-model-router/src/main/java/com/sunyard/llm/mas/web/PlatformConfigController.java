package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.PlatformConfigService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 平台配置端点：系统参数 / 安全基线 / 成本预警。
 * 统一落 mas_platform_config（此前只在前端内存里，保存后刷新即回退）。
 */
@RestController
public class PlatformConfigController {

    private final PlatformConfigService configService;

    public PlatformConfigController(PlatformConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/internal/system/params")
    public Mono<Map<String, Object>> getSystemParams() {
        return configService.get("SYSTEM_PARAMS", defaultSystemParams());
    }

    @PutMapping("/internal/system/params")
    public Mono<Map<String, Object>> saveSystemParams(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return configService.save("SYSTEM_PARAMS", "系统参数", body, operator);
    }

    @GetMapping("/internal/security/baseline")
    public Mono<Map<String, Object>> getSecurityBaseline() {
        return configService.get("SECURITY_BASELINE", defaultSecurityBaseline());
    }

    @PutMapping("/internal/security/baseline")
    public Mono<Map<String, Object>> saveSecurityBaseline(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return configService.save("SECURITY_BASELINE", "安全基线", body, operator);
    }

    @GetMapping("/internal/metering/cost-alert")
    public Mono<Map<String, Object>> getCostAlert() {
        return configService.get("COST_ALERT", defaultCostAlert());
    }

    @PutMapping("/internal/metering/cost-alert")
    public Mono<Map<String, Object>> saveCostAlert(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return configService.save("COST_ALERT", "成本预警", body, operator);
    }

    /** 通用配置读取（检测模块 / 词库 / 检测模型 / 优化建议 / 举报反馈 / 模型卡片 / 广场申请 / 节点 / 异构调度 / 引擎 / 应急 等统一落 mas_platform_config） */
    @GetMapping("/internal/system/config/{key}")
    public Mono<Object> getConfig(@PathVariable("key") String key) {
        return configService.getJson(key);
    }

    @PutMapping("/internal/system/config/{key}")
    public Mono<Map<String, Object>> saveConfig(
            @PathVariable("key") String key,
            @RequestBody Object body,
            @RequestHeader(value = "X-Operator", required = false) String operator) {
        return configService.saveJson(key, key, body, operator);
    }

    /* ---------------- 未配置时的默认口径（与前端初始展示一致） ---------------- */

    private static Map<String, Object> defaultSystemParams() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("pwdMinLen", 10);
        m.put("sessionTimeoutMin", 30);
        m.put("loginFailLock", 5);
        m.put("auditRetentionDays", 365);
        m.put("ipWhitelistEnabled", true);
        m.put("dataMasking", true);
        m.put("auditExportApproval", true);
        m.put("pwdHistoryNoRepeat", 5);
        m.put("opLogDetailLevel", "DETAIL");
        m.put("notifyChannels", "SITE,MAIL");
        m.put("loginAnnounceEnabled", true);
        return m;
    }

    private static Map<String, Object> defaultSecurityBaseline() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("guardrailEnabled", true);
        m.put("sensitiveAction", "MASK");
        m.put("auditFullContent", true);
        m.put("adminTokenRequired", true);
        m.put("failClosed", false);
        return m;
    }

    private static Map<String, Object> defaultCostAlert() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("enabled", true);
        m.put("dailyBudget", 750_000);
        m.put("warnPct", 85);
        m.put("overAction", "DOWNGRADE");
        m.put("notifyChannels", "SITE,MAIL");
        return m;
    }
}

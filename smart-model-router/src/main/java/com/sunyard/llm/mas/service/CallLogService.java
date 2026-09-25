package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 调用记录写入 mas_call_log（fire-and-forget，失败仅告警）。
 */
@Service
public class CallLogService {

    private static final Logger log = LoggerFactory.getLogger(CallLogService.class);

    private final CallLogMapper mapper;
    private final TenantService tenantService;
    private final PricingService pricingService;

    /** 单条内容留存上限（字符），避免超大请求撑爆存储 */
    private static final int CONTENT_LIMIT = 8000;

    public CallLogService(CallLogMapper mapper, TenantService tenantService, PricingService pricingService) {
        this.mapper = mapper;
        this.tenantService = tenantService;
        this.pricingService = pricingService;
    }

    public void logAsync(PipelineContext ctx, int promptTokens, int completionTokens,
                         int totalTokens, long totalCostMs, boolean success) {
        String appId = emptyIfNull(ctx.getAppId());
        String tenantId = appToTenant(appId);
        String requestContent = truncate(ctx.getRequest() == null ? "" : ctx.getRequest().toString());
        String serviceType = ctx.getRequest() != null && ctx.getRequest().path("service_type").isTextual()
                ? ctx.getRequest().path("service_type").asText() : "chat";
        String billMonth = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        java.math.BigDecimal cost = pricingService == null ? null
                : pricingService.price(null, appId, null, serviceType, emptyIfNull(ctx.getRequestedModel()),
                promptTokens, completionTokens);
        ReactiveDbAdapter.monoVoid(() -> mapper.insertCallLogFull(
                        ctx.getTraceId() == null ? "" : ctx.getTraceId(),
                        appId,
                        ctx.getUserId(),
                        emptyIfNull(ctx.getAgentId()),
                        emptyIfNull(ctx.getRequestedModel()),
                        emptyIfNull(ctx.getIntent()),
                        ctx.getMeta().isCacheHit() ? 1 : 0,
                        emptyIfNull(ctx.getMeta().getCacheLevel()),
                        emptyIfNull(ctx.getMeta().getRoutedTo()),
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        (int) ctx.getMeta().getPipelineCostMs(),
                        (int) totalCostMs,
                        success ? 0 : 1,
                        tenantId,
                        null,                       // dept_id：由部门↔租户映射在计量侧解析
                        null,                       // sla_level：来自应用注册表，后续补齐
                        null,                       // data_level
                        null,                       // scenario
                        serviceType,
                        requestContent,
                        "",                         // response_content：响应完成后回填
                        sha256(requestContent),
                        cost,
                        billMonth))
                .subscribe(null, e -> log.warn("Call log write failed: {}", e.getMessage()));
    }

    /** 响应完成后回填响应内容（审计留存），并重算防篡改哈希 */
    public void fillResponseAsync(String traceId, String responseContent) {
        if (traceId == null || traceId.isEmpty()) return;
        String content = truncate(responseContent == null ? "" : responseContent);
        ReactiveDbAdapter.monoVoid(() -> mapper.updateResponseContent(traceId, content, sha256(content)))
                .subscribe(null, e -> log.warn("response content fill failed: {}", e.getMessage()));
    }

    /** 审计内容查询（二-8） */
    public java.util.Map<String, Object> getContent(String traceId) {
        return mapper.selectContent(traceId);
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() <= CONTENT_LIMIT ? s : s.substring(0, CONTENT_LIMIT));
    }

    private static String sha256(String raw) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 根据 app_id 推导 tenant_id：
     * 【已改造】原为 Java 硬编码 switch（招标二-1 违反"可配置"口径），
     * 现改为查询 mas_app_tenant 映射表（TenantService 带 60s 缓存），支持在线维护。
     */
    private String appToTenant(String appId) {
        if (appId == null || appId.isBlank()) return "";
        if (tenantService == null) return "";
        String tenant = tenantService.resolveTenantByApp(appId);
        return "UNKNOWN".equals(tenant) ? "" : tenant;
    }

    /** MyBatis 不接受 null，统一以空串占位 */
    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}

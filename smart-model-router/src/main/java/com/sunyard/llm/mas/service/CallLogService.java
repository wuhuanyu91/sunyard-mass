package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 调用记录写入 mas_call_log（fire-and-forget，失败仅告警）。
 * <p>
 * 【改造背景】此前 request_content 恒写入、response_content 恒写空串、
 * dept_id / sla_level / data_level / scenario 恒写 null，
 * 审计追溯只有元数据没有内容，且部门维度无法在库里落地（招标二-8 判定为"部分"）。
 * 现在：按应用画像补齐分级维度、按数据分级策略决定是否留存原文并对原文脱敏、
 * 响应完成后回填响应内容并重算请求+响应的防篡改哈希。
 */
@Service
public class CallLogService {

    private static final Logger log = LoggerFactory.getLogger(CallLogService.class);

    private final CallLogMapper mapper;
    private final TenantService tenantService;
    private final PricingService pricingService;
    private final AppProfileService appProfileService;
    private final DataLevelPolicyService dataLevelPolicyService;
    private final MasProperties props;

    /** 单条内容留存上限（字符），避免超大请求撑爆存储 */
    private static final int CONTENT_LIMIT = 8000;

    public CallLogService(CallLogMapper mapper, TenantService tenantService, PricingService pricingService,
                          AppProfileService appProfileService, DataLevelPolicyService dataLevelPolicyService,
                          MasProperties props) {
        this.mapper = mapper;
        this.tenantService = tenantService;
        this.pricingService = pricingService;
        this.appProfileService = appProfileService;
        this.dataLevelPolicyService = dataLevelPolicyService;
        this.props = props;
    }

    public void logAsync(PipelineContext ctx, int promptTokens, int completionTokens,
                         int totalTokens, long totalCostMs, boolean success) {
        logAsync(ctx, promptTokens, completionTokens, totalTokens, totalCostMs, success, null);
    }

    /**
     * 写入调用记录；responseContent 非空时一并落库（避免先插入后更新产生竞态），
     * 并按「请求 + 响应」计算防篡改哈希。
     */
    public void logAsync(PipelineContext ctx, int promptTokens, int completionTokens,
                         int totalTokens, long totalCostMs, boolean success, String responseContent) {
        String appId = emptyIfNull(ctx.getAppId());
        String tenantId = appToTenant(appId);
        // 数据分级维度：来自应用注册表（app_id → 部门 / SLA / 数据等级），此前恒为 null
        String deptId = appProfileService.deptOf(appId);
        String slaLevel = appProfileService.slaOf(appId);
        String dataLevel = appProfileService.dataLevelOf(appId);
        String scenario = resolveScenario(ctx);
        String serviceType = ctx.getRequest() != null && ctx.getRequest().path("service_type").isTextual()
                ? ctx.getRequest().path("service_type").asText() : "chat";
        String billMonth = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        java.math.BigDecimal cost = pricingService == null ? null
                : pricingService.price(deptId, appId, scenario, serviceType, emptyIfNull(ctx.getRequestedModel()),
                promptTokens, completionTokens);

        // 审计内容留存：受总开关 + 该数据等级的分级策略双重控制，留存前按等级强度脱敏
        String rawRequest = ctx.getRequest() == null ? "" : ctx.getRequest().toString();
        boolean retain = contentRetained(dataLevel);
        String requestContent = retain ? mask(truncate(rawRequest), dataLevel) : "[按数据分级策略不留存原文]";
        String savedResponse = responseContent == null ? ""
                : (retain ? mask(truncate(responseContent), dataLevel) : "[按数据分级策略不留存原文]");
        // 防篡改哈希覆盖「请求 + 响应」，任一被改动即可被检出
        String hash = sha256(requestContent + "\n---\n" + savedResponse);

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
                        deptId,
                        slaLevel,
                        dataLevel,
                        scenario,
                        serviceType,
                        requestContent,
                        savedResponse,              // response_content：随调用记录一并落库
                        hash,
                        cost,
                        billMonth))
                .subscribe(null, e -> log.warn("Call log write failed: {}", e.getMessage()));
    }

    /**
     * 审计内容查询（二-8）：返回已脱敏原文，前端 AUDITOR 角色可申请解锁。
     * <p>
     * 注：响应内容随 logAsync 一并落库（单次 INSERT，无先插后补的竞态），
     * 此前的 fillResponseAsync 回填路径已删除 —— 全项目零调用，且旧签名缺省按 L2
     * 处理存在"低等级内容被错误留存"的隐患。
     */
    public java.util.Map<String, Object> getContent(String traceId) {
        return mapper.selectContent(traceId);
    }

    /** 业务场景：请求体显式声明 > 意图类型 */
    private static String resolveScenario(PipelineContext ctx) {
        if (ctx.getRequest() != null && ctx.getRequest().path("scenario").isTextual()
                && !ctx.getRequest().path("scenario").asText().isBlank()) {
            return ctx.getRequest().path("scenario").asText();
        }
        return emptyIfNull(ctx.getIntent());
    }

    private boolean contentRetained(String dataLevel) {
        if (props != null && props.getGovernance() != null && !props.getGovernance().isContentRetentionEnabled()) {
            return false;
        }
        return dataLevelPolicyService == null || dataLevelPolicyService.contentRetained(dataLevel);
    }

    private String mask(String content, String dataLevel) {
        return dataLevelPolicyService == null ? content : dataLevelPolicyService.mask(content, dataLevel);
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

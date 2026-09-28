package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.mapper.PolicyMapper;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 统一控制面策略运行时（需求概览 5.2/六章 + POC 第 11 问）：
 * 把控制面里"已发布"的策略真正作用到请求链路，并在 L1/L3/L4 各阶段写执行留痕，
 * 使"一次请求实际执行了哪些策略"可被完整追溯。
 * <p>
 * 【改造背景】PolicyService.logExecution 接口虽然已就绪，但管线各阶段从未调用，
 * mas_policy_exec_log 恒为空，控制面策略对请求没有任何约束力。
 */
@Service
public class PolicyRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(PolicyRuntimeService.class);
    private static final long CACHE_TTL_MS = 30_000L;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PolicyMapper policyMapper;
    private final PolicyService policyService;
    private final AppProfileService appProfileService;
    private final TenantService tenantService;
    private final MasProperties props;

    /** 已发布策略快照 */
    private volatile List<Snapshot> published = new CopyOnWriteArrayList<>();
    private volatile long loadedAt = 0L;

    public PolicyRuntimeService(PolicyMapper policyMapper, PolicyService policyService,
                                AppProfileService appProfileService, TenantService tenantService,
                                MasProperties props) {
        this.policyMapper = policyMapper;
        this.policyService = policyService;
        this.appProfileService = appProfileService;
        this.tenantService = tenantService;
        this.props = props;
    }

    public boolean enabled() {
        return props == null || props.getGovernance() == null || props.getGovernance().isPolicyRuntimeEnabled();
    }

    // ---------------- 快照 ----------------

    private record Snapshot(String policyId, String name, String category, String scope,
                            int version, JsonNode content) {
    }

    private List<Snapshot> published() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < CACHE_TTL_MS && !published.isEmpty()) return published;
        synchronized (this) {
            if (now - loadedAt < CACHE_TTL_MS && !published.isEmpty()) return published;
            try {
                List<Map<String, Object>> rows = policyMapper.listPublishedWithContent();
                List<Snapshot> fresh = new ArrayList<>();
                for (Map<String, Object> r : rows) {
                    JsonNode content = parseContent(r.get("content_json"));
                    fresh.add(new Snapshot(
                            String.valueOf(r.get("policy_id")),
                            String.valueOf(r.get("name")),
                            String.valueOf(r.get("category")),
                            r.get("scope") == null ? "GLOBAL" : String.valueOf(r.get("scope")),
                            r.get("current_version") == null ? 0 : ((Number) r.get("current_version")).intValue(),
                            content));
                }
                published = new CopyOnWriteArrayList<>(fresh);
                loadedAt = now;
            } catch (Exception e) {
                log.warn("published policy load failed (keep previous): {}", e.getMessage());
                if (published.isEmpty()) published = new CopyOnWriteArrayList<>();
            }
            return published;
        }
    }

    /** 策略发布/回滚后立即生效 */
    public void refreshNow() {
        synchronized (this) {
            loadedAt = 0L;
        }
        published();
    }

    private static JsonNode parseContent(Object raw) {
        if (raw == null) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(String.valueOf(raw));
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    // ---------------- 匹配 ----------------

    /** 判断策略作用域是否覆盖当前请求 */
    private boolean scopeMatches(String scope, PipelineContext ctx) {
        if (scope == null || scope.isBlank()) return true;
        String s = scope.trim().toUpperCase();
        if ("GLOBAL".equals(s)) return true;
        String appId = ctx.getAppId() == null ? "" : ctx.getAppId();
        return switch (s) {
            case "APP" -> !appId.isBlank() && scope.contains(appId);
            case "DEPT" -> {
                String dept = appProfileService == null ? "" : appProfileService.deptOf(appId);
                yield !dept.isBlank() && scope.contains(dept);
            }
            case "TENANT" -> {
                String tenant = tenantService == null ? "" : tenantService.resolveTenantByApp(appId);
                yield !tenant.isBlank() && scope.contains(tenant);
            }
            case "MODEL" -> ctx.getRequestedModel() != null && scope.contains(ctx.getRequestedModel());
            default -> true;
        };
    }

    private List<Snapshot> match(PipelineContext ctx, String category) {
        List<Snapshot> out = new ArrayList<>();
        for (Snapshot p : published()) {
            if (category != null && !category.equalsIgnoreCase(p.category())) continue;
            if (scopeMatches(p.scope(), ctx)) out.add(p);
        }
        return out;
    }

    private void trace(PipelineContext ctx, Snapshot p, String stage, String decision, String detail) {
        policyService.logExecution(ctx.getTraceId(), p.policyId(), p.version(), stage, decision, detail);
    }

    // ---------------- 阶段：L1 安全策略 ----------------

    /**
     * L1 阶段：安全类策略（阻断类）。
     *
     * @return 命中阻断时返回拒绝原因，否则返回 null
     */
    public String evaluateL1(PipelineContext ctx) {
        if (!enabled()) return null;
        for (Snapshot p : match(ctx, "SECURITY")) {
            JsonNode c = p.content();
            boolean block = c.path("block").asBoolean(false);
            if (block) {
                String reason = c.path("block_reason").asText("命中安全策略 " + p.policyId());
                trace(ctx, p, "L1", "BLOCK", reason);
                return reason;
            }
            trace(ctx, p, "L1", "PASS", "安全策略校验通过");
        }
        return null;
    }

    // ---------------- 阶段：L3 路由策略 ----------------

    /** L3 阶段：路由类策略，返回允许的模型清单（为空表示不限制）与降级模型 */
    public RoutingConstraint evaluateL3(PipelineContext ctx) {
        if (!enabled()) return RoutingConstraint.none();
        List<String> allowed = new ArrayList<>();
        String fallback = null;
        Integer latencyCeil = null;
        for (Snapshot p : match(ctx, "ROUTING")) {
            JsonNode c = p.content();
            JsonNode models = c.path("allowed_models");
            if (models.isArray() && !models.isEmpty()) {
                for (JsonNode m : models) {
                    String v = m.asText("").trim();
                    if (!v.isEmpty() && !allowed.contains(v)) allowed.add(v);
                }
                trace(ctx, p, "L3", "APPLY", "限定候选模型：" + allowed);
            } else {
                trace(ctx, p, "L3", "PASS", "策略未限定模型");
            }
            if (c.path("fallback_model").isTextual() && fallback == null) {
                fallback = c.path("fallback_model").asText(null);
            }
            if (c.path("latency_ceil_ms").isInt() && latencyCeil == null) {
                latencyCeil = c.path("latency_ceil_ms").asInt();
            }
        }
        return new RoutingConstraint(allowed, fallback, latencyCeil);
    }

    public record RoutingConstraint(List<String> allowedModels, String fallbackModel, Integer latencyCeilMs) {
        public static RoutingConstraint none() {
            return new RoutingConstraint(List.of(), null, null);
        }

        public boolean allows(String modelId) {
            return allowedModels.isEmpty() || allowedModels.contains(modelId);
        }
    }

    // ---------------- 阶段：L4 计量/配额策略 ----------------

    /** L4 阶段：计量类策略，返回单请求 Token 上限（0 = 不限制） */
    public int evaluateL4(PipelineContext ctx) {
        if (!enabled()) return 0;
        int ceiling = 0;
        for (Snapshot p : match(ctx, "METERING")) {
            JsonNode c = p.content();
            int limit = c.path("max_tokens_per_request").asInt(0);
            if (limit > 0 && (ceiling == 0 || limit < ceiling)) {
                ceiling = limit;
                trace(ctx, p, "L4", "APPLY", "单请求 Token 上限 " + limit);
            } else {
                trace(ctx, p, "L4", "PASS", "策略未设 Token 上限");
            }
        }
        return ceiling;
    }

    /** 供前端"已执行策略"面板查看：某 trace 的策略执行记录由 PolicyService.listExecLogs 提供 */
    public List<Map<String, Object>> execLogs(String traceId) {
        return policyMapper.listExecLogs(traceId);
    }
}

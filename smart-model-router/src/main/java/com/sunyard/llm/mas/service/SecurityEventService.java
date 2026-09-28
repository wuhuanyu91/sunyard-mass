package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunyard.llm.mas.mapper.AlertMapper;
import com.sunyard.llm.mas.mapper.SecurityEventMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全事件检测引擎（公告二-7 行为监测与异常识别）：
 * <p>
 * 【改造背景】此前 mas_security_event 全项目无任何 INSERT 路径，页面数据 100% 来自迁移脚本种子，
 * 且大盘告警数在 DashboardService 里写死为常量。现提供：
 * 1. 检测规则在线维护（mas_security_rule）；
 * 2. 基于 mas_call_log 的异常检测（高频调用 / Token 突增 / 非工作时段 / 被拦截请求），
 *    由 @Scheduled 定时执行（默认 5 分钟，可用 mas.security.scan-interval-ms 调整），也可手动触发；
 * 3. 管线拦截实时落事件（黑名单/敏感词/限流/策略/鉴权失败，fire-and-forget 不影响主链路）；
 * 4. 告警处置闭环（确认 → 开始处置 → 关闭，含处置意见留痕）。
 */
@Service
public class SecurityEventService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventService.class);
    private static final String MODULE = "security";
    private static final ObjectMapper OM = new ObjectMapper();

    private final SecurityEventMapper eventMapper;
    private final AlertMapper alertMapper;
    private final OpLogService opLogService;
    private final TenantService tenantService;

    public SecurityEventService(SecurityEventMapper eventMapper, AlertMapper alertMapper,
                                OpLogService opLogService, TenantService tenantService) {
        this.eventMapper = eventMapper;
        this.alertMapper = alertMapper;
        this.opLogService = opLogService;
        this.tenantService = tenantService;
    }

    // ---------------- 检测规则 ----------------

    public Mono<List<Map<String, Object>>> listRules() {
        return ReactiveDbAdapter.mono(eventMapper::listRules);
    }

    public Mono<Map<String, Object>> createRule(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String ruleCode = str(body.get("ruleCode"));
            if (ruleCode.isEmpty()) throw new IllegalArgumentException("ruleCode 必填");
            eventMapper.insertRule(ruleCode, str(body.getOrDefault("ruleName", ruleCode)),
                    str(body.getOrDefault("eventType", "ANOMALY")),
                    str(body.getOrDefault("severity", "MEDIUM")),
                    str(body.getOrDefault("conditionJson", "{}")),
                    str(body.get("action")), intVal(body.get("status"), 1));
            return ruleCode;
        }).flatMap(c -> opLogService.record(MODULE, "新增检测规则", operator, c, "创建安全检测规则"));
    }

    public Mono<Map<String, Object>> updateRule(String ruleCode, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            eventMapper.updateRule(ruleCode, str(body.get("ruleName")), str(body.get("eventType")),
                    str(body.get("severity")), str(body.get("conditionJson")), str(body.get("action")),
                    intVal(body.get("status"), 1));
            return ruleCode;
        }).flatMap(c -> opLogService.record(MODULE, "修改检测规则", operator, c, "更新安全检测规则"));
    }

    public Mono<Map<String, Object>> deleteRule(String ruleCode, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            eventMapper.deleteRule(ruleCode);
            return ruleCode;
        }).flatMap(c -> opLogService.record(MODULE, "删除检测规则", operator, c, "删除安全检测规则"));
    }

    // ---------------- 检测执行 ----------------

    /**
     * 执行一轮检测：按启用规则扫描 mas_call_log，命中即写入 mas_security_event 并生成告警。
     *
     * @return 本轮新增事件数、告警数
     */
    public Mono<Map<String, Object>> scan(String operator) {
        return scanCore().flatMap(res -> opLogService.record(MODULE, "安全检测扫描", operator, null,
                "新增事件 " + res.get("events_created") + " 条").thenReturn(res));
    }

    /**
     * 定时检测（公告二-7 实时性要求）：默认每 5 分钟一轮，间隔可用 mas.security.scan-interval-ms 调整。
     * 有新增事件时才留操作日志，避免空转刷屏。
     */
    @Scheduled(fixedDelayString = "${mas.security.scan-interval-ms:300000}",
            initialDelayString = "${mas.security.scan-initial-delay-ms:60000}")
    public void scheduledScan() {
        scanCore()
                .flatMap(res -> Integer.parseInt(String.valueOf(res.get("events_created"))) > 0
                        ? opLogService.record(MODULE, "定时安全检测", "SYSTEM", null,
                        "自动扫描新增事件 " + res.get("events_created") + " 条").thenReturn(res)
                        : Mono.just(res))
                .subscribe(res -> log.debug("scheduled security scan: events={}", res.get("events_created")),
                        e -> log.warn("scheduled security scan failed: {}", e.getMessage()));
    }

    private Mono<Map<String, Object>> scanCore() {
        return ReactiveDbAdapter.mono(() -> {
            List<Map<String, Object>> rules = eventMapper.listRules();
            int events = 0;
            int alerts = 0;
            for (Map<String, Object> rule : rules) {
                if (!Integer.valueOf(1).equals(rule.get("status"))) continue;
                JsonNode cond = parseCond(String.valueOf(rule.get("condition_json")));
                int windowMinutes = cond.path("windowMinutes").asInt(60);
                long threshold = cond.path("threshold").asLong(100);
                LocalDateTime since = LocalDateTime.now().minusMinutes(windowMinutes);
                String ruleCode = String.valueOf(rule.get("rule_code"));
                String eventType = String.valueOf(rule.get("event_type"));
                String severity = String.valueOf(rule.get("severity"));

                List<Map<String, Object>> hits = switch (eventType) {
                    case "RATE_ABUSE", "HIGH_FREQ" -> eventMapper.detectHighFrequency(since, threshold);
                    case "TOKEN_SURGE", "QUOTA_ABUSE" -> eventMapper.detectTokenSurge(since, threshold);
                    case "OFF_HOUR" -> eventMapper.detectOffHour(since, threshold);
                    case "BLOCKED" -> eventMapper.detectBlocked(since);
                    default -> List.of();
                };

                for (Map<String, Object> hit : hits) {
                    String subject = String.valueOf(hit.getOrDefault("user_id",
                            hit.getOrDefault("app_id", "unknown")));
                    String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    String eventId = "SEC-" + sha256(ruleCode + subject + day).substring(0, 16);
                    String detail = describe(eventType, hit);
                    int n = eventMapper.insertEvent(eventId, null,
                            strOrNull(hit.get("tenant_id")), strOrNull(hit.get("user_id")),
                            strOrNull(hit.get("app_id")), strOrNull(hit.get("model_id")),
                            eventType, severity, "RUNTIME", ruleCode,
                            String.valueOf(rule.get("rule_name")), false,
                            "BLOCK".equals(String.valueOf(rule.get("action"))),
                            eventType, detail, "FULL", sha256(eventId + detail), LocalDateTime.now());
                    if (n > 0) {
                        events++;
                        if (isHigh(severity)) {
                            String alertId = "ALT-" + sha256(ruleCode + subject + day).substring(0, 12);
                            openAlert(alertId, severity, detail,
                                    String.valueOf(rule.get("rule_name")), null);
                            alerts++;
                        }
                    }
                }
            }
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("events_created", events);
            res.put("alerts_opened", alerts);
            res.put("scanned_at", LocalDateTime.now().toString());
            log.info("security scan done: events={}, alerts={}", events, alerts);
            return res;
        });
    }

    // ---------------- 管线拦截实时落事件 ----------------

    /**
     * 管线拦截即时写入安全事件（fire-and-forget：异步落库，失败仅告警，绝不影响主链路）：
     * L1/L3 拦截（黑名单 / 敏感词 / 限流 / 策略 / 鉴权失败 / 数据分级拒绝）发生时调用，
     * 高危级别自动开告警；同 (事件类型 + 主体 + 日) 幂等去重（ON CONFLICT DO NOTHING）。
     */
    public void recordPipelineEvent(String traceId, String appId, String userId, String modelId,
                                    String guardrailStage, String eventType, String severity,
                                    String reasonCode, String reasonText) {
        ReactiveDbAdapter.monoVoid(() -> {
            String subject = userId != null && !userId.isBlank() ? userId
                    : (appId != null && !appId.isBlank() ? appId : "unknown");
            String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String eventId = "SEC-" + sha256(eventType + subject + day).substring(0, 16);
            String tenantId = tenantOfApp(appId);
            int n = eventMapper.insertEvent(eventId, traceId, tenantId,
                    strOrNull(userId), strOrNull(appId), modelId,
                    eventType, severity, guardrailStage, null, null, false, true,
                    reasonCode, reasonText, "FULL", sha256(eventId + reasonText), LocalDateTime.now());
            if (n > 0) {
                log.info("pipeline security event recorded: type={}, subject={}, stage={}",
                        eventType, subject, guardrailStage);
                if (isHigh(severity)) {
                    String alertId = "ALT-" + sha256(eventType + subject + day).substring(0, 12);
                    openAlert(alertId, severity, reasonText, guardrailStage + " 管线拦截", traceId);
                }
            }
        }).subscribe(null, e -> log.warn("pipeline security event write failed: {}", e.getMessage()));
    }

    /** 开告警（幂等插入；同 ID 已存在说明告警未关闭，不覆盖处置进度） */
    private void openAlert(String alertId, String severity, String detail, String source, String traceId) {
        try {
            alertMapper.insertAlertIfAbsent(alertId, severity,
                    "[" + severity + "] 安全告警：" + source, detail, traceId);
        } catch (Exception e) {
            log.warn("open alert failed ({}): {}", alertId, e.getMessage());
        }
    }

    private String tenantOfApp(String appId) {
        if (appId == null || appId.isBlank() || tenantService == null) return null;
        String tenant = tenantService.resolveTenantByApp(appId);
        return "UNKNOWN".equals(tenant) ? null : tenant;
    }

    // ---------------- 事件查询 ----------------

    public Mono<Map<String, Object>> listEvents(String eventType, String eventLevel, String tenantId,
                                                String userId, int hours, int page, int size) {
        return ReactiveDbAdapter.mono(() -> {
            LocalDateTime since = hours > 0 ? LocalDateTime.now().minusHours(hours) : null;
            int limit = size > 0 ? size : 20;
            int offset = Math.max(page - 1, 0) * limit;
            List<Map<String, Object>> rows = eventMapper.listEvents(eventType, eventLevel, tenantId, userId,
                    since, limit, offset);
            long total = eventMapper.countEvents(eventType, eventLevel, since);
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("events", rows);
            res.put("total", (int) total);
            return res;
        });
    }

    // ---------------- 告警处置闭环 ----------------

    public Mono<List<Map<String, Object>>> listAlerts() {
        return ReactiveDbAdapter.mono(alertMapper::listAlerts);
    }

    /**
     * 告警处置：确认(ACKNOWLEDGED) → 开始处置(HANDLING) → 关闭(CLOSED)，带处置意见留痕。
     */
    public Mono<Map<String, Object>> handleAlert(String alertId, String status, String comment, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            if (alertMapper.exists(alertId) == null) {
                throw new IllegalArgumentException("告警不存在：" + alertId);
            }
            alertMapper.updateAlertStatus(alertId, status, comment);
            return alertId;
        }).flatMap(id -> opLogService.record(MODULE, "告警处置", operator, id,
                "状态置为 " + status + (comment == null ? "" : "，意见：" + comment)));
    }

    // ---------------- helpers ----------------

    private static boolean isHigh(String severity) {
        return "HIGH".equalsIgnoreCase(severity) || "CRITICAL".equalsIgnoreCase(severity);
    }

    private static String describe(String eventType, Map<String, Object> hit) {
        return switch (eventType) {
            case "RATE_ABUSE", "HIGH_FREQ" -> "窗口内调用次数 " + hit.get("cnt") + " 次，触发高频阈值";
            case "TOKEN_SURGE", "QUOTA_ABUSE" -> "窗口内 Token 消耗 " + hit.get("tokens") + "，触发突增阈值";
            case "OFF_HOUR" -> "非工作时段（22:00-06:00）调用 " + hit.get("cnt") + " 次";
            case "BLOCKED" -> "请求被安全策略拦截";
            default -> "命中检测规则";
        };
    }

    private static JsonNode parseCond(String json) {
        try {
            return json == null || json.isBlank() ? OM.createObjectNode() : OM.readTree(json);
        } catch (Exception e) {
            return OM.createObjectNode();
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String strOrNull(Object v) {
        return v == null ? null : String.valueOf(v);
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

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }
}

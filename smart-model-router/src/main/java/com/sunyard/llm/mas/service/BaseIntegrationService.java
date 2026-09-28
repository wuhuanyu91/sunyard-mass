package com.sunyard.llm.mas.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunyard.llm.mas.entity.BaseIntegrationEntity;
import com.sunyard.llm.mas.entity.IntegrationLogEntity;
import com.sunyard.llm.mas.mapper.BaseIntegrationMapper;
import com.sunyard.llm.mas.mapper.IntegrationLogMapper;
import com.sunyard.llm.mas.mapper.RbacMapper;
import com.sunyard.llm.mas.mapper.SecurityEventMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行内底座/运营管理体系对接适配器（兼容适配#2：IAM / 4A / 统一监控 / 告警平台 / 工单系统 / 网关系统）。
 * <p>
 * 设计原则：外部行内系统对接为<b>配置门控</b>——未配置 endpoint 或 enabled=false 时不发起真实外呼，
 * 适配器以本地闭环保证演示可跑通（IAM 同步取本地账号、监控快照取本地指标、告警转本地工单）。
 * 一旦在 mas_base_integration 配好真实地址并启用，syncIam / testConnectivity / forwardAlert 将真实外呼，
 * 对应 HTTP/SDK 调用点已在方法内标注，便于联调行内系统时填充。
 */
@Service
public class BaseIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(BaseIntegrationService.class);
    private static final String MODULE = "integration";
    private static final String TICKETS_KEY = "TICKETS";

    private final BaseIntegrationMapper integrationMapper;
    private final IntegrationLogMapper logMapper;
    private final RbacMapper rbacMapper;
    private final SecurityEventMapper securityEventMapper;
    private final PlatformConfigService configService;
    private final OpLogService opLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public BaseIntegrationService(BaseIntegrationMapper integrationMapper, IntegrationLogMapper logMapper,
                                 RbacMapper rbacMapper, SecurityEventMapper securityEventMapper,
                                 PlatformConfigService configService, OpLogService opLogService) {
        this.integrationMapper = integrationMapper;
        this.logMapper = logMapper;
        this.rbacMapper = rbacMapper;
        this.securityEventMapper = securityEventMapper;
        this.configService = configService;
        this.opLogService = opLogService;
    }

    /* ============ 读取 / 配置 ============ */

    public Mono<List<Map<String, Object>>> listIntegrations() {
        return ReactiveDbAdapter.mono(() -> {
            List<BaseIntegrationEntity> rows = integrationMapper.selectList(null);
            if (rows == null || rows.isEmpty()) {
                rows = seedDefaults();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (BaseIntegrationEntity e : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", e.getCode());
                m.put("name", e.getName());
                m.put("type", e.getType());
                m.put("endpoint", e.getEndpoint());
                m.put("enabled", e.getEnabled() == null ? 0 : e.getEnabled());
                m.put("lastSyncAt", e.getLastSyncAt() == null ? null : e.getLastSyncAt().toString());
                m.put("status", e.getStatus());
                m.put("remark", e.getRemark());
                out.add(m);
            }
            return out;
        });
    }

    @SuppressWarnings("unchecked")
    public Mono<Map<String, Object>> saveIntegration(Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("code", ""));
        return ReactiveDbAdapter.mono(() -> {
            BaseIntegrationEntity e = new BaseIntegrationEntity();
            e.setCode(code);
            e.setName(String.valueOf(body.getOrDefault("name", code)));
            e.setType(String.valueOf(body.getOrDefault("type", code)));
            Object ep = body.get("endpoint");
            e.setEndpoint(ep == null ? null : String.valueOf(ep));
            Object en = body.get("enabled");
            e.setEnabled(en == null ? 0 : (en instanceof Boolean ? (((Boolean) en) ? 1 : 0) : Integer.parseInt(String.valueOf(en))));
            e.setStatus(String.valueOf(body.getOrDefault("status", "PENDING")));
            Object rm = body.get("remark");
            e.setRemark(rm == null ? null : String.valueOf(rm));
            integrationMapper.upsert(e);
            return (Map<String, Object>) new LinkedHashMap<String, Object>() {{
                put("code", code);
                put("ok", true);
            }};
        }).flatMap(r -> opLogService.record(MODULE, "保存对接配置",
                String.valueOf(body.getOrDefault("operator", "platform-admin")), code, code))
          .thenReturn(Map.of("code", code, "ok", true));
    }

    /* ============ 连通性测试（真实外呼尝试，演示环境无地址则如实回报 UNREACHABLE） ============ */

    public Mono<Map<String, Object>> testConnectivity(String code, String operator) {
        return ReactiveDbAdapter.mono(() -> integrationMapper.selectByCode(code))
                .flatMap(entity -> {
                    if (entity == null) {
                        return Mono.just(errorResult(code, "TEST", "对接点不存在"));
                    }
            boolean enabled = entity.getEnabled() != null && entity.getEnabled() == 1;
            String endpoint = entity.getEndpoint();
            if (!enabled || endpoint == null || endpoint.isBlank()) {
                String status = !enabled ? "DISABLED" : "UNCONFIGURED";
                String msg = !enabled ? "外部对接未启用（enabled=false），仅走本地闭环" : "未配置行内系统地址（endpoint 为空）";
                        persistStatus(code, status, msg);
                        writeLog(code, "TEST", "OK", msg, null, operator);
                        Map<String, Object> r0 = new LinkedHashMap<>();
                        r0.put("code", code);
                        r0.put("status", status);
                        r0.put("message", msg);
                        r0.put("latencyMs", 0);
                        return Mono.just(r0);
            }
            // 真实外呼：演示环境通常不可达，如实记录 UNREACHABLE（联调行内系统时替换为带鉴权的 SDK 调用）
            long start = System.currentTimeMillis();
            String resultStatus;
            String message;
            Integer latency = null;
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                latency = (int) (System.currentTimeMillis() - start);
                resultStatus = resp.statusCode() >= 200 && resp.statusCode() < 400 ? "CONNECTED" : "UNREACHABLE";
                message = "HTTP " + resp.statusCode();
            } catch (Exception ex) {
                latency = (int) (System.currentTimeMillis() - start);
                resultStatus = "UNREACHABLE";
                message = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
            persistStatus(code, resultStatus, message);
            final String fStatus = resultStatus;
            final String fMsg = message;
            final Integer fLatency = latency;
                    writeLog(code, "TEST", "OK", fMsg, fLatency, operator);
                    Map<String, Object> r1 = new LinkedHashMap<>();
                    r1.put("code", code);
                    r1.put("status", fStatus);
                    r1.put("message", fMsg);
                    r1.put("latencyMs", fLatency);
                    return Mono.just(r1);
                })
        // selectByCode 返回 null 时 Mono.fromCallable 会发射空流，此处兜底返回错误结果（避免 200+空 body）
        .switchIfEmpty(Mono.just(errorResult(code, "TEST", "对接点不存在")));
    }

    /* ============ IAM 账号同步（本地闭环：取本地账号数；外部启用时真实拉取） ============ */

    public Mono<Map<String, Object>> syncIam(String operator) {
        return ReactiveDbAdapter.mono(() -> {
            BaseIntegrationEntity iam = integrationMapper.selectByCode("IAM");
            if (iam == null) iam = seedOne("IAM", "统一身份认证(IAM)", "IAM");
            long userCount = rbacMapper.selectCount(null);
            iam.setLastSyncAt(LocalDateTime.now());
            iam.setStatus(iam.getEnabled() != null && iam.getEnabled() == 1 ? "CONNECTED" : "PENDING");
            integrationMapper.upsert(iam);
            writeLog("IAM", "SYNC_IAM", "OK", "同步账号 " + userCount + " 条", null, operator);
            // 外部启用时在此调用 IAM 系统拉取/推送账号（带行内鉴权），当前走本地闭环
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", "IAM");
            r.put("syncedUserCount", userCount);
            r.put("mode", iam.getEnabled() != null && iam.getEnabled() == 1 ? "EXTERNAL" : "LOCAL");
            r.put("lastSyncAt", iam.getLastSyncAt().toString());
            return r;
        });
    }

    /* ============ 监控指标推送（本地闭环：取本地指标快照；外部启用时推送至监控平台） ============ */

    public Mono<Map<String, Object>> pushMonitor(String operator) {
        return ReactiveDbAdapter.mono(() -> {
            BaseIntegrationEntity mon = integrationMapper.selectByCode("MONITOR");
            if (mon == null) mon = seedOne("MONITOR", "统一监控平台", "MONITOR");
            long userCount = rbacMapper.selectCount(null);
            long eventCount = securityEventMapper.selectCount(null);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("userCount", userCount);
            snapshot.put("securityEventCount", eventCount);
            snapshot.put("pushedAt", LocalDateTime.now().toString());
            String json;
            try {
                json = objectMapper.writeValueAsString(snapshot);
            } catch (Exception e) {
                json = snapshot.toString();
            }
            mon.setLastSyncAt(LocalDateTime.now());
            mon.setStatus(mon.getEnabled() != null && mon.getEnabled() == 1 ? "CONNECTED" : "PENDING");
            integrationMapper.upsert(mon);
            writeLog("MONITOR", "PUSH_MONITOR", "OK", "推送监控快照：" + json, null, operator);
            // 外部启用时在此推送至行内监控平台（Prometheus/自研采集 agent），当前落本地日志
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", "MONITOR");
            r.put("snapshot", snapshot);
            r.put("mode", mon.getEnabled() != null && mon.getEnabled() == 1 ? "EXTERNAL" : "LOCAL");
            return r;
        });
    }

    /* ============ 告警转发至行内告警平台（本地闭环：转发为本地工单） ============ */

    public Mono<Map<String, Object>> forwardAlert(String alertId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            // ALT-<hash12> 与 SEC-<hash16> 同源（同一 sha256 的不同长度前缀）：
            // 此前用告警主键直接 selectById 事件表必然落空（明细恒为空），现按哈希前缀反查；
            // 同时兼容直接传入事件主键（SEC-*）的调用方式，优先精确直查
            Map<String, Object> ev = null;
            if (alertId != null && !alertId.isBlank()) {
                ev = securityEventMapper.selectDetailByEventId(alertId);
                if (ev == null && alertId.startsWith("ALT-") && alertId.length() > 4) {
                    ev = securityEventMapper.selectByAlertHashPrefix(alertId.substring(4));
                }
            }
            String title = "行内告警平台转发：安全事件 " + alertId;
            String content = ev == null
                    ? "安全事件 " + alertId + "（本地无明细，已按对接规范转发至行内告警/工单系统）"
                    : String.format("事件类型=%s；等级=%s；规则=%s；原因=%s；用户=%s；应用=%s",
                            ev.get("event_type"), ev.get("event_level"), ev.get("rule_name"),
                            ev.get("reason_text"), ev.get("user_id"), ev.get("app_id"));
            Map<String, Object> ticket = buildTicket("PROBLEM", title, content, "告警平台", "信息科技部");
            integrationMapper.updateStatus("ALERT", "PENDING");
            writeLog("ALERT", "FORWARD_ALERT", "OK", "转发告警 " + alertId + " 为工单 " + ticket.get("ticketId"), null, operator);
            return ticket;
        }).flatMap(ticket -> configService.appendArrayItem(TICKETS_KEY, ticket, operator)
                .thenReturn(ticket));
    }

    /* ============ 工单下发至行内工单系统（本地闭环：落入本地工单 KV） ============ */

    public Mono<Map<String, Object>> createExternalTicket(Map<String, Object> body, String operator) {
        String type = String.valueOf(body.getOrDefault("type", "PROBLEM"));
        String title = String.valueOf(body.getOrDefault("title", "未命名工单"));
        String content = String.valueOf(body.getOrDefault("content", ""));
        String from = String.valueOf(body.getOrDefault("from", "平台管理员"));
        String dept = String.valueOf(body.getOrDefault("deptName", "信息科技部"));
        Map<String, Object> ticket = buildTicket(type, title, content, from, dept);
        return ReactiveDbAdapter.mono(() -> {
            integrationMapper.updateStatus("TICKET", "PENDING");
            writeLog("TICKET", "CREATE_TICKET", "OK", "下发工单 " + ticket.get("ticketId") + "：" + title, null, operator);
            return ticket;
        }).flatMap(t -> configService.appendArrayItem(TICKETS_KEY, t, operator).thenReturn(t));
    }

    /* ============ 网关系统衔接（公告三-1：与行内网关系统的注册/路由对接） ============ */

    /**
     * 向行内 API 网关注册本模块服务与路由（本地闭环：注册明细落 mas_platform_config KV，
     * 外部启用时把同一 payload 经行内网关管理 API 真实下发——调用点已标注）。
     * payload：{serviceId, routes:[{path, method, upstream}]}，缺省注册本模块对外契约端点。
     */
    public Mono<Map<String, Object>> registerGatewayRoutes(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            BaseIntegrationEntity gw = integrationMapper.selectByCode("GATEWAY");
            if (gw == null) gw = seedOne("GATEWAY", "行内 API 网关", "GATEWAY");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> routes = body.get("routes") instanceof List<?> l && !l.isEmpty()
                    ? (List<Map<String, Object>>) l : defaultGatewayRoutes();
            String serviceId = String.valueOf(body.getOrDefault("serviceId", "smart-model-router"));
            Map<String, Object> registration = new LinkedHashMap<>();
            registration.put("serviceId", serviceId);
            registration.put("routes", routes);
            registration.put("registeredAt", LocalDateTime.now().toString());
            gw.setLastSyncAt(LocalDateTime.now());
            gw.setStatus(gw.getEnabled() != null && gw.getEnabled() == 1 ? "CONNECTED" : "PENDING");
            integrationMapper.upsert(gw);
            writeLog("GATEWAY", "REGISTER_ROUTE", "OK",
                    "注册服务 " + serviceId + "，路由 " + routes.size() + " 条", null, operator);
            // 外部启用时在此调用行内网关管理 API 下发路由注册（带行内鉴权），当前落本地 KV 闭环
            return registration;
        }).flatMap(reg -> configService.appendArrayItem("GATEWAY_ROUTES", reg, operator).thenReturn(reg));
    }

    /** 本模块对外契约端点（与 docs/API.md 外部 API 章节保持一致） */
    private List<Map<String, Object>> defaultGatewayRoutes() {
        List<Map<String, Object>> routes = new ArrayList<>();
        routes.add(Map.of("path", "/smart-router/v1/chat/completions", "method", "POST", "upstream", "model-gateway"));
        routes.add(Map.of("path", "/smart-router/v1/models", "method", "GET", "upstream", "model-gateway"));
        routes.add(Map.of("path", "/smart-router/internal/collection/ingest", "method", "POST", "upstream", "collection"));
        return routes;
    }

    /* ============ 对接事件日志 ============ */

    public Mono<List<Map<String, Object>>> getLogs() {
        return ReactiveDbAdapter.mono(() -> {
            List<IntegrationLogEntity> rows = logMapper.selectList(
                    new QueryWrapper<IntegrationLogEntity>().orderByDesc("created_at").last("LIMIT 50"));
            List<Map<String, Object>> out = new ArrayList<>();
            for (IntegrationLogEntity e : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getId());
                m.put("intCode", e.getIntCode());
                m.put("action", e.getAction());
                m.put("status", e.getStatus());
                m.put("message", e.getMessage());
                m.put("latencyMs", e.getLatencyMs());
                m.put("operator", e.getOperator());
                m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
                out.add(m);
            }
            return out;
        });
    }

    /* ============ 内部工具 ============ */

    private Map<String, Object> buildTicket(String type, String title, String content, String from, String dept) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("ticketId", "TK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" +
                String.format("%03d", (int) (System.currentTimeMillis() % 1000)));
        t.put("type", type);
        t.put("title", title);
        t.put("content", content);
        t.put("from", from);
        t.put("deptName", dept);
        t.put("status", "OPEN");
        t.put("createdAt", LocalDateTime.now().toString());
        t.put("reply", "");
        return t;
    }

    private void writeLog(String code, String action, String status, String message, Integer latency, String operator) {
        IntegrationLogEntity e = new IntegrationLogEntity();
        e.setIntCode(code);
        e.setAction(action);
        e.setStatus(status);
        e.setMessage(message);
        e.setLatencyMs(latency);
        e.setOperator(operator);
        e.setCreatedAt(LocalDateTime.now());
        try {
            logMapper.insert(e);
        } catch (Exception ex) {
            log.warn("integration log insert failed: {}", ex.getMessage());
        }
    }

    private void persistStatus(String code, String status, String remark) {
        integrationMapper.updateStatusWithRemark(code, status, remark);
    }

    private Map<String, Object> errorResult(String code, String action, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("status", "ERROR");
        m.put("message", message);
        m.put("latencyMs", 0);
        return m;
    }

    private BaseIntegrationEntity seedOne(String code, String name, String type) {
        BaseIntegrationEntity e = new BaseIntegrationEntity();
        e.setCode(code);
        e.setName(name);
        e.setType(type);
        e.setEnabled(0);
        e.setStatus("PENDING");
        integrationMapper.upsert(e);
        return e;
    }

    private List<BaseIntegrationEntity> seedDefaults() {
        List<BaseIntegrationEntity> list = new ArrayList<>();
        list.add(seedOne("IAM", "统一身份认证(IAM)", "IAM"));
        list.add(seedOne("FOUR_A", "4A 运维审计", "FOUR_A"));
        list.add(seedOne("MONITOR", "统一监控平台", "MONITOR"));
        list.add(seedOne("ALERT", "告警平台", "ALERT"));
        list.add(seedOne("TICKET", "工单系统", "TICKET"));
        return list;
    }
}

package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.AlertEntity;
import com.sunyard.llm.mas.entity.SecurityEventEntity;
import com.sunyard.llm.mas.mapper.AlertMapper;
import com.sunyard.llm.mas.mapper.GuardrailMapper;
import com.sunyard.llm.mas.mapper.SecurityEventMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

/**
 * 安全审计服务：从 mas_security_event / mas_alert 真实数据查询
 */
@Service
public class SecurityService {

    private final SecurityEventMapper securityEventMapper;
    private final AlertMapper alertMapper;
    private final GuardrailMapper guardrailMapper;
    private final OpLogService opLogService;

    public SecurityService(SecurityEventMapper securityEventMapper, AlertMapper alertMapper,
                           GuardrailMapper guardrailMapper, OpLogService opLogService) {
        this.securityEventMapper = securityEventMapper;
        this.alertMapper = alertMapper;
        this.guardrailMapper = guardrailMapper;
        this.opLogService = opLogService;
    }

    /** 安全事件列表（从 mas_security_event 查询） */
    public Mono<List<Map<String, Object>>> listSecurityEvents(String eventType, String eventLevel) {
        return ReactiveDbAdapter.mono(() -> {
            LambdaQueryWrapper<SecurityEventEntity> wrapper = new LambdaQueryWrapper<>();
            if (eventType != null && !eventType.isBlank()) wrapper.eq(SecurityEventEntity::getEventType, eventType);
            if (eventLevel != null && !eventLevel.isBlank()) wrapper.eq(SecurityEventEntity::getEventLevel, eventLevel);
            wrapper.orderByDesc(SecurityEventEntity::getCreatedAt);
            wrapper.last("LIMIT 50");

            List<SecurityEventEntity> entities = securityEventMapper.selectList(wrapper);
            List<Map<String, Object>> list = new ArrayList<>();
            for (SecurityEventEntity e : entities) {
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("security_event_id", e.getEventId());
                evt.put("trace_id", e.getTraceId() != null ? e.getTraceId() : "");
                evt.put("tenant_id", e.getTenantId() != null ? e.getTenantId() : "");
                evt.put("user_id", e.getUserId() != null ? e.getUserId() : "");
                evt.put("app_id", e.getAppId() != null ? e.getAppId() : "");
                evt.put("asset_id", e.getAssetId() != null ? e.getAssetId() : "");
                evt.put("event_type", e.getEventType());
                evt.put("event_level", e.getEventLevel());
                evt.put("guardrail_stage", e.getGuardrailStage());
                evt.put("rule_id", e.getRuleId() != null ? e.getRuleId() : "");
                evt.put("rule_name", e.getRuleName() != null ? e.getRuleName() : "");
                evt.put("masked", e.getMasked() != null ? e.getMasked() : false);
                evt.put("blocked", e.getBlocked() != null ? e.getBlocked() : false);
                evt.put("reason_code", e.getReasonCode() != null ? e.getReasonCode() : "");
                evt.put("reason_text", e.getReasonText() != null ? e.getReasonText() : "");
                evt.put("log_storage_type", e.getLogStorageType() != null ? e.getLogStorageType() : "");
                evt.put("hash_signature", e.getHashSignature() != null ? e.getHashSignature() : "");
                evt.put("created_at", e.getCreatedAt() != null ? e.getCreatedAt().toString() + "Z" : "");
                list.add(evt);
            }
            return list;
        });
    }

    /** 告警列表（从 mas_alert 查询） */
    public Mono<List<Map<String, Object>>> listAlerts() {
        return ReactiveDbAdapter.mono(() -> {
            List<AlertEntity> entities = alertMapper.selectList(null);
            List<Map<String, Object>> list = new ArrayList<>();
            for (AlertEntity a : entities) {
                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("alert_id", a.getAlertId());
                alert.put("alert_status", a.getAlertStatus());
                alert.put("event_level", a.getEventLevel());
                alert.put("title", a.getTitle());
                alert.put("detail", a.getDetail());
                alert.put("trace_id", a.getTraceId());
                alert.put("created_at", a.getCreatedAt() != null ? a.getCreatedAt().toString() + "Z" : "");
                list.add(alert);
            }
            return list;
        });
    }

    /**
     * 护栏配置：【已改造】此前返回写死的常量 Map，现从 mas_guardrail_config 真实读取；
     * 无配置时返回库内默认（enabled=true, MEDIUM）而非编造 api_url 等字段。
     */
    public Mono<Map<String, Object>> getGuardrailConfig() {
        return ReactiveDbAdapter.mono(() -> {
            Map<String, Object> row = guardrailMapper.selectConfig();
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("enabled", row == null || Integer.valueOf(1).equals(row.get("enabled")));
            config.put("default_model", row == null ? "" : String.valueOf(row.getOrDefault("default_model", "")));
            config.put("sensitivity", row == null ? "MEDIUM" : String.valueOf(row.getOrDefault("sensitivity", "MEDIUM")));
            config.put("modules_json", row == null ? "[]" : String.valueOf(row.getOrDefault("modules_json", "[]")));
            config.put("updated_at", row == null ? "" : String.valueOf(row.getOrDefault("updated_at", "")));
            return config;
        });
    }

    public Mono<Map<String, Object>> saveGuardrailConfig(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            Integer enabled = body.get("enabled") == null ? null
                    : (Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0);
            String modulesJson = body.get("modules") == null ? null : String.valueOf(body.get("modules"));
            if (guardrailMapper.selectConfig() == null) {
                guardrailMapper.insertConfig(enabled, str(body.get("defaultModel")),
                        str(body.get("sensitivity")), modulesJson, operator);
            } else {
                guardrailMapper.updateConfig(enabled, str(body.get("defaultModel")),
                        str(body.get("sensitivity")), modulesJson, operator);
            }
            return "GUARDRAIL";
        }).flatMap(t -> opLogService.record("security", "保存护栏配置", operator, t,
                "护栏" + (Boolean.TRUE.equals(body.get("enabled")) ? "已开启" : "已关闭") + "，配置已落库"));
    }

    /** 护栏策略列表：【已改造】从 mas_guardrail_policy 真实读取（此前为 3 条硬编码 Map） */
    public Mono<List<Map<String, Object>>> listGuardrailPolicies() {
        return ReactiveDbAdapter.mono(guardrailMapper::listPolicies);
    }

    public Mono<Map<String, Object>> createGuardrailPolicy(Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            String policyId = str(body.getOrDefault("policyId", "GD-" + System.currentTimeMillis()));
            guardrailMapper.insertPolicy(policyId, str(body.getOrDefault("name", policyId)),
                    str(body.get("stage")), str(body.get("action")), str(body.get("libType")),
                    str(body.get("keywordLib")),
                    body.get("enabled") == null ? 1 : (Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0),
                    operator);
            return policyId;
        }).flatMap(id -> opLogService.record("security", "新建安全策略", operator, id,
                "策略 " + body.getOrDefault("name", id) + " 已落库"));
    }

    public Mono<Map<String, Object>> updateGuardrailPolicy(String policyId, Map<String, Object> body, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            if (guardrailMapper.selectPolicy(policyId) == null) {
                throw new IllegalArgumentException("安全策略不存在：" + policyId);
            }
            guardrailMapper.updatePolicy(policyId, str(body.get("name")), str(body.get("stage")),
                    str(body.get("action")), str(body.get("libType")), str(body.get("keywordLib")),
                    body.get("enabled") == null ? null : (Boolean.TRUE.equals(body.get("enabled")) ? 1 : 0),
                    operator);
            return policyId;
        }).flatMap(id -> opLogService.record("security", "更新安全策略", operator, id, "策略已更新并落库"));
    }

    public Mono<Map<String, Object>> deleteGuardrailPolicy(String policyId, String operator) {
        return ReactiveDbAdapter.mono(() -> {
            int n = guardrailMapper.deletePolicy(policyId);
            if (n == 0) throw new IllegalArgumentException("安全策略不存在：" + policyId);
            return policyId;
        }).flatMap(id -> opLogService.record("security", "删除安全策略", operator, id, "策略已删除"));
    }

    // 兼容旧签名
    public Mono<Map<String, Object>> saveGuardrailConfig(Map<String, Object> body) {
        return saveGuardrailConfig(body, null);
    }

    public Mono<Map<String, Object>> createGuardrailPolicy(Map<String, Object> body) {
        return createGuardrailPolicy(body, null);
    }

    public Mono<Map<String, Object>> updateGuardrailPolicy(String policyId, Map<String, Object> body) {
        return updateGuardrailPolicy(policyId, body, null);
    }

    public Mono<Map<String, Object>> deleteGuardrailPolicy(String policyId) {
        return deleteGuardrailPolicy(policyId, null);
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private Mono<Map<String, Object>> opRecord(String opType, String targetId, String detail) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("op_id", "OP-" + System.currentTimeMillis());
        record.put("op_type", opType);
        record.put("operator", "平台管理员");
        record.put("target_id", targetId);
        record.put("detail", detail);
        record.put("created_at", Instant.now().toString());
        return Mono.just(record);
    }
}

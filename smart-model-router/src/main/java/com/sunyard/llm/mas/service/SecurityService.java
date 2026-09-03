package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.AlertEntity;
import com.sunyard.llm.mas.entity.SecurityEventEntity;
import com.sunyard.llm.mas.mapper.AlertMapper;
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

    public SecurityService(SecurityEventMapper securityEventMapper, AlertMapper alertMapper) {
        this.securityEventMapper = securityEventMapper;
        this.alertMapper = alertMapper;
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

    /** 护栏配置（系统配置，保持） */
    public Mono<Map<String, Object>> getGuardrailConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("api_url", "https://guardrail.nbmaas.local/api/v1");
        config.put("api_key_masked", "gd-****f3a9");
        config.put("text_latency_ms", 200);
        config.put("multimodal_latency_ms", 1200);
        return Mono.just(config);
    }

    public Mono<Map<String, Object>> saveGuardrailConfig(Map<String, Object> body) {
        return opRecord("保存护栏规则", "GUARDRAIL",
                "护栏" + (Boolean.TRUE.equals(body.get("enabled")) ? "已开启" : "已关闭"));
    }

    /** 护栏策略列表（配置数据，保持） */
    public Mono<List<Map<String, Object>>> listGuardrailPolicies() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("policy_id", "GD-001", "name", "零售客服输出护栏",
                "desc", "客服场景输出脱敏 + 合规检测",
                "modules", List.of("PRIVACY", "COMPLIANCE", "BAD_INFO"),
                "action", "MASK", "bind_apps", List.of("APP-CSR")));
        list.add(Map.of("policy_id", "GD-002", "name", "研发输入强校验",
                "desc", "防提示注入 + 恶意代码识别",
                "modules", List.of("INJECTION", "MALCODE", "ABUSE"),
                "action", "BLOCK", "bind_apps", List.of("APP-AICODING")));
        list.add(Map.of("policy_id", "GD-003", "name", "全行输入合规底线",
                "desc", "违法/不良信息全场景拦截",
                "modules", List.of("ILLEGAL", "BAD_INFO", "COMPLIANCE"),
                "action", "BLOCK", "bind_apps", List.of()));
        return Mono.just(list);
    }

    public Mono<Map<String, Object>> createGuardrailPolicy(Map<String, Object> body) {
        return opRecord("新建安全策略", "GD-NEW", body.getOrDefault("name", "").toString());
    }

    public Mono<Map<String, Object>> updateGuardrailPolicy(String policyId, Map<String, Object> body) {
        return opRecord("更新安全策略", policyId, body.getOrDefault("name", "").toString());
    }

    public Mono<Map<String, Object>> deleteGuardrailPolicy(String policyId) {
        return opRecord("删除安全策略", policyId, "策略已删除");
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

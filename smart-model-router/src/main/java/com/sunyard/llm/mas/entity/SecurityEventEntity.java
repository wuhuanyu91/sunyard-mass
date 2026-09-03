package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("mas_security_event")
public class SecurityEventEntity {

    @TableId
    private String eventId;
    private String traceId;
    private String tenantId;
    private String userId;
    private String appId;
    private String assetId;
    private String eventType;
    private String eventLevel;
    private String guardrailStage;
    private String ruleId;
    private String ruleName;
    private Boolean masked;
    private Boolean blocked;
    private String reasonCode;
    private String reasonText;
    private String logStorageType;
    private String hashSignature;
    private LocalDateTime createdAt;

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAssetId() { return assetId; }
    public void setAssetId(String assetId) { this.assetId = assetId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventLevel() { return eventLevel; }
    public void setEventLevel(String eventLevel) { this.eventLevel = eventLevel; }
    public String getGuardrailStage() { return guardrailStage; }
    public void setGuardrailStage(String guardrailStage) { this.guardrailStage = guardrailStage; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Boolean getMasked() { return masked; }
    public void setMasked(Boolean masked) { this.masked = masked; }
    public Boolean getBlocked() { return blocked; }
    public void setBlocked(Boolean blocked) { this.blocked = blocked; }
    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getReasonText() { return reasonText; }
    public void setReasonText(String reasonText) { this.reasonText = reasonText; }
    public String getLogStorageType() { return logStorageType; }
    public void setLogStorageType(String logStorageType) { this.logStorageType = logStorageType; }
    public String getHashSignature() { return hashSignature; }
    public void setHashSignature(String hashSignature) { this.hashSignature = hashSignature; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

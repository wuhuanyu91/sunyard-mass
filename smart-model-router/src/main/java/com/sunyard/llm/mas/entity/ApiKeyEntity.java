package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_api_key API Key 表实体（§8 已知限制消除 — 鉴权体系）
 */
@TableName("mas_api_key")
public class ApiKeyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyHash;

    private String keyPrefix;

    private String userId;

    private String appId;

    private Integer status;

    private LocalDateTime expireAt;

    private LocalDateTime createdAt;

    /** §1.4 自助申请元数据 */
    private String teamName;
    private String agentName;
    private String agentType;
    private String purpose;
    private String quotaTier;
    private String createdBy;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getQuotaTier() { return quotaTier; }
    public void setQuotaTier(String quotaTier) { this.quotaTier = quotaTier; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}

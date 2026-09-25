package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 租户（公告二-1 多租户 / 二-2 租户隔离）
 */
@TableName("mas_tenant")
public class TenantEntity {

    @TableId
    private Long id;
    private String tenantId;
    private String tenantName;
    private Integer status;          // 1=启用 0=停用（停用即收回模型与数据权限）
    private String isolationMode;    // RLS/SCHEMA/NONE
    private Long quotaTokens;
    private String contact;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getTenantName() { return tenantName; }
    public void setTenantName(String tenantName) { this.tenantName = tenantName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getIsolationMode() { return isolationMode; }
    public void setIsolationMode(String isolationMode) { this.isolationMode = isolationMode; }
    public Long getQuotaTokens() { return quotaTokens; }
    public void setQuotaTokens(Long quotaTokens) { this.quotaTokens = quotaTokens; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

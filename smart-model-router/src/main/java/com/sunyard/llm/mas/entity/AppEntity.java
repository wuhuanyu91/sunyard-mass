package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_app 应用注册表实体（§1.5 应用身份统一管控 - 阶段 1）
 */
@TableName("mas_app")
public class AppEntity {

    @TableId
    private String appId;

    private String appName;

    private String appNameEn;

    private String deptId;

    private String ownerId;

    private String ownerEmail;

    private String slaLevel;

    private String dataLevel;

    private Integer status;

    private Long monthQuota;

    private String description;

    private String approvedBy;

    private LocalDateTime approvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getAppNameEn() { return appNameEn; }
    public void setAppNameEn(String appNameEn) { this.appNameEn = appNameEn; }
    public String getDeptId() { return deptId; }
    public void setDeptId(String deptId) { this.deptId = deptId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getOwnerEmail() { return ownerEmail; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public String getSlaLevel() { return slaLevel; }
    public void setSlaLevel(String slaLevel) { this.slaLevel = slaLevel; }
    public String getDataLevel() { return dataLevel; }
    public void setDataLevel(String dataLevel) { this.dataLevel = dataLevel; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getMonthQuota() { return monthQuota; }
    public void setMonthQuota(Long monthQuota) { this.monthQuota = monthQuota; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

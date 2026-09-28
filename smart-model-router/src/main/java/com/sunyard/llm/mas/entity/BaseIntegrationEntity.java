package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_base_integration：行内底座/运营管理体系对接配置（兼容适配#2）。
 * <p>
 * 每条记录对应一个对接点：IAM 统一身份认证 / 4A 运维审计 / 统一监控平台 / 告警平台 / 工单系统。
 * 外部行内系统对接为配置门控：未配置 endpoint 或 enabled=false 时不发起真实外呼，
 * 适配器以本地闭环（IAM 同步取本地账号、监控快照取本地指标、告警转本地工单）保证演示可跑通。
 */
@TableName("mas_base_integration")
public class BaseIntegrationEntity {

    /** IAM / FOUR_A / MONITOR / ALERT / TICKET */
    @TableId
    private String code;
    private String name;
    /** IAM / FOUR_A / MONITOR / ALERT / TICKET */
    private String type;
    /** 行内系统对接地址；演示环境留空表示未联调 */
    private String endpoint;
    /** 0 未启用外部对接 / 1 启用真实外呼 */
    private Integer enabled;
    private LocalDateTime lastSyncAt;
    /** PENDING / CONNECTED / UNREACHABLE / DISABLED */
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(LocalDateTime lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

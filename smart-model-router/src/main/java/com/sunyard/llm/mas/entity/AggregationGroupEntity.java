package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_aggregation_group：模型聚合组（多模型按策略统一对外提供同一逻辑服务）。
 */
@TableName("mas_aggregation_group")
public class AggregationGroupEntity {

    @TableId
    private String groupId;
    private String name;
    /** 成员模型，逗号分隔 */
    private String members;
    /** ROUND_ROBIN / WEIGHTED / LATENCY */
    private String strategy;
    private Integer autoSkipFault;
    private Integer healthCheckSec;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMembers() { return members; }
    public void setMembers(String members) { this.members = members; }
    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }
    public Integer getAutoSkipFault() { return autoSkipFault; }
    public void setAutoSkipFault(Integer autoSkipFault) { this.autoSkipFault = autoSkipFault; }
    public Integer getHealthCheckSec() { return healthCheckSec; }
    public void setHealthCheckSec(Integer healthCheckSec) { this.healthCheckSec = healthCheckSec; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

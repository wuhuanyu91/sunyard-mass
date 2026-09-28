package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * mas_elastic_switch：弹性切换配置（算力水位持续超阈值时切入租赁/云端资源池），单行配置。
 */
@TableName("mas_elastic_switch")
public class ElasticSwitchEntity {

    private Integer id;
    private BigDecimal triggerUtil;
    private Integer sustainMin;
    /** RENTAL / CLOUD / LOCAL */
    private String target;
    private BigDecimal trafficRatio;
    private Integer active;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public BigDecimal getTriggerUtil() { return triggerUtil; }
    public void setTriggerUtil(BigDecimal triggerUtil) { this.triggerUtil = triggerUtil; }
    public Integer getSustainMin() { return sustainMin; }
    public void setSustainMin(Integer sustainMin) { this.sustainMin = sustainMin; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public BigDecimal getTrafficRatio() { return trafficRatio; }
    public void setTrafficRatio(BigDecimal trafficRatio) { this.trafficRatio = trafficRatio; }
    public Integer getActive() { return active; }
    public void setActive(Integer active) { this.active = active; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

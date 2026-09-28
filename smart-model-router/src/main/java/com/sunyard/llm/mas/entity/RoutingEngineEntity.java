package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * mas_routing_engine：路由引擎四维权重与策略开关（单行配置，id 固定为 1）。
 * <p>
 * 【改造背景】此前 getRoutingEngine 返回硬编码常量、saveRoutingEngine 只返回操作留痕不落库，
 * 页面提示"保存成功"刷新即回原值。
 */
@TableName("mas_routing_engine")
public class RoutingEngineEntity {

    private Integer id;
    private BigDecimal weightLatency;
    private BigDecimal weightCost;
    private BigDecimal weightRisk;
    private BigDecimal weightLoad;
    private Integer cacheFirst;
    private Integer budgetGuard;
    private Integer slaPriority;
    private Integer autoFallback;
    private Integer openaiCompat;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public BigDecimal getWeightLatency() { return weightLatency; }
    public void setWeightLatency(BigDecimal weightLatency) { this.weightLatency = weightLatency; }
    public BigDecimal getWeightCost() { return weightCost; }
    public void setWeightCost(BigDecimal weightCost) { this.weightCost = weightCost; }
    public BigDecimal getWeightRisk() { return weightRisk; }
    public void setWeightRisk(BigDecimal weightRisk) { this.weightRisk = weightRisk; }
    public BigDecimal getWeightLoad() { return weightLoad; }
    public void setWeightLoad(BigDecimal weightLoad) { this.weightLoad = weightLoad; }
    public Integer getCacheFirst() { return cacheFirst; }
    public void setCacheFirst(Integer cacheFirst) { this.cacheFirst = cacheFirst; }
    public Integer getBudgetGuard() { return budgetGuard; }
    public void setBudgetGuard(Integer budgetGuard) { this.budgetGuard = budgetGuard; }
    public Integer getSlaPriority() { return slaPriority; }
    public void setSlaPriority(Integer slaPriority) { this.slaPriority = slaPriority; }
    public Integer getAutoFallback() { return autoFallback; }
    public void setAutoFallback(Integer autoFallback) { this.autoFallback = autoFallback; }
    public Integer getOpenaiCompat() { return openaiCompat; }
    public void setOpenaiCompat(Integer openaiCompat) { this.openaiCompat = openaiCompat; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_compute_orchestration：弹性算力编排配置（单行，id 固定为 1）。
 * 覆盖混部 / 优先级隔离 / 连续批处理 / 前缀 KV 缓存 / 投机解码五组能力。
 */
@TableName("mas_compute_orchestration")
public class OrchestrationEntity {

    private Integer id;
    private Integer mixedDeployEnabled;
    private String affinityModels;
    private Integer memoryReservePct;
    private Integer prioWeightP0;
    private Integer prioWeightP1;
    private Integer prioWeightP2;
    private Integer lowPrioQueueing;
    private Integer allowP0Preempt;
    private Integer continuousBatch;
    private Integer batchMaxSize;
    private Integer prefixKvCache;
    private String kvStrategy;
    private Integer kvTenantIsolate;
    private Integer kvSensitiveForbidden;
    private Integer kvTtlMin;
    private Integer speculativeDecode;
    private String draftModel;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getMixedDeployEnabled() { return mixedDeployEnabled; }
    public void setMixedDeployEnabled(Integer v) { this.mixedDeployEnabled = v; }
    public String getAffinityModels() { return affinityModels; }
    public void setAffinityModels(String v) { this.affinityModels = v; }
    public Integer getMemoryReservePct() { return memoryReservePct; }
    public void setMemoryReservePct(Integer v) { this.memoryReservePct = v; }
    public Integer getPrioWeightP0() { return prioWeightP0; }
    public void setPrioWeightP0(Integer v) { this.prioWeightP0 = v; }
    public Integer getPrioWeightP1() { return prioWeightP1; }
    public void setPrioWeightP1(Integer v) { this.prioWeightP1 = v; }
    public Integer getPrioWeightP2() { return prioWeightP2; }
    public void setPrioWeightP2(Integer v) { this.prioWeightP2 = v; }
    public Integer getLowPrioQueueing() { return lowPrioQueueing; }
    public void setLowPrioQueueing(Integer v) { this.lowPrioQueueing = v; }
    public Integer getAllowP0Preempt() { return allowP0Preempt; }
    public void setAllowP0Preempt(Integer v) { this.allowP0Preempt = v; }
    public Integer getContinuousBatch() { return continuousBatch; }
    public void setContinuousBatch(Integer v) { this.continuousBatch = v; }
    public Integer getBatchMaxSize() { return batchMaxSize; }
    public void setBatchMaxSize(Integer v) { this.batchMaxSize = v; }
    public Integer getPrefixKvCache() { return prefixKvCache; }
    public void setPrefixKvCache(Integer v) { this.prefixKvCache = v; }
    public String getKvStrategy() { return kvStrategy; }
    public void setKvStrategy(String v) { this.kvStrategy = v; }
    public Integer getKvTenantIsolate() { return kvTenantIsolate; }
    public void setKvTenantIsolate(Integer v) { this.kvTenantIsolate = v; }
    public Integer getKvSensitiveForbidden() { return kvSensitiveForbidden; }
    public void setKvSensitiveForbidden(Integer v) { this.kvSensitiveForbidden = v; }
    public Integer getKvTtlMin() { return kvTtlMin; }
    public void setKvTtlMin(Integer v) { this.kvTtlMin = v; }
    public Integer getSpeculativeDecode() { return speculativeDecode; }
    public void setSpeculativeDecode(Integer v) { this.speculativeDecode = v; }
    public String getDraftModel() { return draftModel; }
    public void setDraftModel(String v) { this.draftModel = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}

package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_call_log 调用记录表实体
 */
@TableName("mas_call_log")
public class CallLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private String appId;

    private String userId;

    private String agentId;

    private String modelId;

    private String intentType;

    private Integer cacheHit;

    private String cacheLevel;

    private String routedTo;

    private Integer promptTokens;

    private Integer completionTokens;

    private Integer totalTokens;

    private Integer pipelineCostMs;

    private Integer totalCostMs;

    private Integer status;

    private LocalDateTime createdAt;

    private String tenantId;

    private String slaLevel;

    private String dataLevel;

    /** 差异化计量维度（公告一-5） */
    private String deptId;
    private String scenario;
    private String serviceType;

    /** 计价引擎写入的单笔成本（元），取代前端 0.0016 / 0.00025 硬算 */
    private java.math.BigDecimal costAmount;

    /** 审计内容留存（公告二-8） */
    private String requestContent;
    private String responseContent;
    private String contentHash;

    /** 归属账期 YYYY-MM（计费结算） */
    private String billMonth;

    private Integer billed;

    public String getDeptId() {
        return deptId;
    }

    public void setDeptId(String deptId) {
        this.deptId = deptId;
    }

    public String getScenario() {
        return scenario;
    }

    public void setScenario(String scenario) {
        this.scenario = scenario;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(String serviceType) {
        this.serviceType = serviceType;
    }

    public java.math.BigDecimal getCostAmount() {
        return costAmount;
    }

    public void setCostAmount(java.math.BigDecimal costAmount) {
        this.costAmount = costAmount;
    }

    public String getRequestContent() {
        return requestContent;
    }

    public void setRequestContent(String requestContent) {
        this.requestContent = requestContent;
    }

    public String getResponseContent() {
        return responseContent;
    }

    public void setResponseContent(String responseContent) {
        this.responseContent = responseContent;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getBillMonth() {
        return billMonth;
    }

    public void setBillMonth(String billMonth) {
        this.billMonth = billMonth;
    }

    public Integer getBilled() {
        return billed;
    }

    public void setBilled(Integer billed) {
        this.billed = billed;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getIntentType() {
        return intentType;
    }

    public void setIntentType(String intentType) {
        this.intentType = intentType;
    }

    public Integer getCacheHit() {
        return cacheHit;
    }

    public void setCacheHit(Integer cacheHit) {
        this.cacheHit = cacheHit;
    }

    public String getCacheLevel() {
        return cacheLevel;
    }

    public void setCacheLevel(String cacheLevel) {
        this.cacheLevel = cacheLevel;
    }

    public String getRoutedTo() {
        return routedTo;
    }

    public void setRoutedTo(String routedTo) {
        this.routedTo = routedTo;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(Integer totalTokens) {
        this.totalTokens = totalTokens;
    }

    public Integer getPipelineCostMs() {
        return pipelineCostMs;
    }

    public void setPipelineCostMs(Integer pipelineCostMs) {
        this.pipelineCostMs = pipelineCostMs;
    }

    public Integer getTotalCostMs() {
        return totalCostMs;
    }

    public void setTotalCostMs(Integer totalCostMs) {
        this.totalCostMs = totalCostMs;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSlaLevel() {
        return slaLevel;
    }

    public void setSlaLevel(String slaLevel) {
        this.slaLevel = slaLevel;
    }

    public String getDataLevel() {
        return dataLevel;
    }

    public void setDataLevel(String dataLevel) {
        this.dataLevel = dataLevel;
    }
}

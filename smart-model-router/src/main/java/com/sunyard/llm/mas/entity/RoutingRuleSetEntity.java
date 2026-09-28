package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_routing_rule_set：场景路由规则集（信贷审批 / 风控反欺诈 / 营销触达 / 客服问答）。
 */
@TableName("mas_routing_rule_set")
public class RoutingRuleSetEntity {

    @TableId
    private String sceneKey;
    private String sceneName;
    private String priority;
    /** 允许模型清单，逗号分隔 */
    private String allowedModels;
    private String fallbackModel;
    private Integer latencyCeilMs;
    private String policyId;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getSceneKey() { return sceneKey; }
    public void setSceneKey(String sceneKey) { this.sceneKey = sceneKey; }
    public String getSceneName() { return sceneName; }
    public void setSceneName(String sceneName) { this.sceneName = sceneName; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getAllowedModels() { return allowedModels; }
    public void setAllowedModels(String allowedModels) { this.allowedModels = allowedModels; }
    public String getFallbackModel() { return fallbackModel; }
    public void setFallbackModel(String fallbackModel) { this.fallbackModel = fallbackModel; }
    public Integer getLatencyCeilMs() { return latencyCeilMs; }
    public void setLatencyCeilMs(Integer latencyCeilMs) { this.latencyCeilMs = latencyCeilMs; }
    public String getPolicyId() { return policyId; }
    public void setPolicyId(String policyId) { this.policyId = policyId; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

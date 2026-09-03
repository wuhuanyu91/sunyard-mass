package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("mas_routing_rule")
public class RoutingRuleEntity {

    @TableId
    private String ruleId;
    private String name;
    private String targetType;
    private String targetId;
    private Boolean enabled;
    private Integer qpsLimit;
    private Integer inputTokenLimit;
    private Integer outputTokenLimit;
    private Integer concurrency;
    private String overAction;
    @TableField("hits_24h")
    private Integer hits24h;
    private LocalDateTime createdAt;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Integer getQpsLimit() { return qpsLimit; }
    public void setQpsLimit(Integer qpsLimit) { this.qpsLimit = qpsLimit; }
    public Integer getInputTokenLimit() { return inputTokenLimit; }
    public void setInputTokenLimit(Integer inputTokenLimit) { this.inputTokenLimit = inputTokenLimit; }
    public Integer getOutputTokenLimit() { return outputTokenLimit; }
    public void setOutputTokenLimit(Integer outputTokenLimit) { this.outputTokenLimit = outputTokenLimit; }
    public Integer getConcurrency() { return concurrency; }
    public void setConcurrency(Integer concurrency) { this.concurrency = concurrency; }
    public String getOverAction() { return overAction; }
    public void setOverAction(String overAction) { this.overAction = overAction; }
    public Integer getHits24h() { return hits24h; }
    public void setHits24h(Integer hits24h) { this.hits24h = hits24h; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

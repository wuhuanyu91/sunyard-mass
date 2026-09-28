package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_data_level_policy：数据分级差异化管控策略。
 * <p>
 * 【改造背景】mas_call_log 的 data_level / sla_level 此前只是被写入与展示的字段，
 * 平台没有任何"按等级执行差异化管控"的逻辑（招标二-5 数据保护判定为"弱"）。
 */
@TableName("mas_data_level_policy")
public class DataLevelPolicyEntity {

    /** L1 / L2 / L3 */
    @TableId
    private String dataLevel;
    /** 允许本地自建算力承载 */
    private Integer allowLocal;
    /** 允许云端模型服务承载 */
    private Integer allowCloud;
    /** 允许外部租赁算力承载 */
    private Integer allowRental;
    /** 脱敏强度：NONE / STANDARD / STRICT */
    private String maskStrength;
    /** 请求/响应原文是否留存（L3 默认不留存原文） */
    private Integer contentRetention;
    /** 日志留存天数 */
    private Integer retentionDays;
    /** 该等级允许的最大上下文长度 */
    private Integer maxContextTokens;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getDataLevel() { return dataLevel; }
    public void setDataLevel(String dataLevel) { this.dataLevel = dataLevel; }
    public Integer getAllowLocal() { return allowLocal; }
    public void setAllowLocal(Integer allowLocal) { this.allowLocal = allowLocal; }
    public Integer getAllowCloud() { return allowCloud; }
    public void setAllowCloud(Integer allowCloud) { this.allowCloud = allowCloud; }
    public Integer getAllowRental() { return allowRental; }
    public void setAllowRental(Integer allowRental) { this.allowRental = allowRental; }
    public String getMaskStrength() { return maskStrength; }
    public void setMaskStrength(String maskStrength) { this.maskStrength = maskStrength; }
    public Integer getContentRetention() { return contentRetention; }
    public void setContentRetention(Integer contentRetention) { this.contentRetention = contentRetention; }
    public Integer getRetentionDays() { return retentionDays; }
    public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
    public Integer getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(Integer maxContextTokens) { this.maxContextTokens = maxContextTokens; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

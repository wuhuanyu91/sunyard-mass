package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 平台配置 KV（系统参数 / 安全基线 / 成本预警）。
 * 【改造背景】这三组配置此前只存在于前端内存：页面保存成功、刷新回原值。
 */
@TableName("mas_platform_config")
public class PlatformConfigEntity {

    @TableId
    private String configKey;
    private String configJson;
    private String operator;
    private LocalDateTime updatedAt;

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

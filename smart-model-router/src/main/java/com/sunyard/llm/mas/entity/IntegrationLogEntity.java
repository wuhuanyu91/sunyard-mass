package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * mas_integration_log：行内底座对接的同步/转发事件留痕。
 * <p>
 * 记录每一次 IAM 同步 / 监控快照推送 / 告警转发 / 工单下发 / 连通性测试的成败与耗时，
 * 供对接审计与排查使用（对应招标二-8 审计追溯要求）。
 */
@TableName("mas_integration_log")
public class IntegrationLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联的对接点 code */
    private String intCode;
    /** SYNC_IAM / PUSH_MONITOR / FORWARD_ALERT / CREATE_TICKET / TEST */
    private String action;
    /** OK / FAIL */
    private String status;
    private String message;
    private Integer latencyMs;
    private String operator;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIntCode() { return intCode; }
    public void setIntCode(String intCode) { this.intCode = intCode; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

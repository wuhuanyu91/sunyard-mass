package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * mas_batch_task：错峰调度任务（批量处理 / 模型评测 / 研发任务排到低负载窗口）。
 */
@TableName("mas_batch_task")
public class BatchTaskEntity {

    @TableId
    private String taskId;
    private String taskName;
    /** BATCH / EVAL / DEV / MIGRATE */
    private String taskType;
    private String targetNode;
    private String targetPool;
    private String windowStart;
    private String windowEnd;
    private String priority;
    /** PENDING / RUNNING / DONE / FAILED / CANCELLED */
    private String status;
    private BigDecimal expectSaving;
    private String sourceSuggestion;
    private String operator;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getTaskId() { return taskId; }
    public void setTaskId(String v) { this.taskId = v; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String v) { this.taskName = v; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String v) { this.taskType = v; }
    public String getTargetNode() { return targetNode; }
    public void setTargetNode(String v) { this.targetNode = v; }
    public String getTargetPool() { return targetPool; }
    public void setTargetPool(String v) { this.targetPool = v; }
    public String getWindowStart() { return windowStart; }
    public void setWindowStart(String v) { this.windowStart = v; }
    public String getWindowEnd() { return windowEnd; }
    public void setWindowEnd(String v) { this.windowEnd = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public BigDecimal getExpectSaving() { return expectSaving; }
    public void setExpectSaving(BigDecimal v) { this.expectSaving = v; }
    public String getSourceSuggestion() { return sourceSuggestion; }
    public void setSourceSuggestion(String v) { this.sourceSuggestion = v; }
    public String getOperator() { return operator; }
    public void setOperator(String v) { this.operator = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}

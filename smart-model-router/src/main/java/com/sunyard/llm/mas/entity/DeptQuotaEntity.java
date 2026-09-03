package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("mas_dept_quota")
public class DeptQuotaEntity {

    @TableId
    private String deptId;
    private String deptName;
    private Long monthTokenQuota;
    private Long usedTokens;
    private Double monthCost;
    private Boolean overLimitStop;
    private Integer warnThreshold;
    private String status;
    private LocalDateTime createdAt;

    public String getDeptId() { return deptId; }
    public void setDeptId(String deptId) { this.deptId = deptId; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String deptName) { this.deptName = deptName; }
    public Long getMonthTokenQuota() { return monthTokenQuota; }
    public void setMonthTokenQuota(Long monthTokenQuota) { this.monthTokenQuota = monthTokenQuota; }
    public Long getUsedTokens() { return usedTokens; }
    public void setUsedTokens(Long usedTokens) { this.usedTokens = usedTokens; }
    public Double getMonthCost() { return monthCost; }
    public void setMonthCost(Double monthCost) { this.monthCost = monthCost; }
    public Boolean getOverLimitStop() { return overLimitStop; }
    public void setOverLimitStop(Boolean overLimitStop) { this.overLimitStop = overLimitStop; }
    public Integer getWarnThreshold() { return warnThreshold; }
    public void setWarnThreshold(Integer warnThreshold) { this.warnThreshold = warnThreshold; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * mas_hetero_vendor：异构算力厂商纳管（英伟达 / 华为昇腾 / 沐曦 / Intel 等）。
 * discover_mode 区分「注册表纳管」与「Agent/Prometheus 自动发现」。
 */
@TableName("mas_hetero_vendor")
public class HeteroVendorEntity {

    @TableId
    private String vendorId;
    private String vendorName;
    /** CUDA / ASCEND / MACA / X86 */
    private String arch;
    private Integer domestic;
    private String cardModels;
    private Integer nodeCount;
    /** ADAPTED / TESTING / INCOMPATIBLE */
    private String adaptStatus;
    private BigDecimal perfRatio;
    private BigDecimal stabilityScore;
    private BigDecimal costPerCardHour;
    /** BALANCED / PREFER / PREFER_DOMESTIC / DISABLED */
    private String schedPolicy;
    private Integer enabled;
    /** REGISTRY / AGENT / PROMETHEUS */
    private String discoverMode;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getVendorId() { return vendorId; }
    public void setVendorId(String v) { this.vendorId = v; }
    public String getVendorName() { return vendorName; }
    public void setVendorName(String v) { this.vendorName = v; }
    public String getArch() { return arch; }
    public void setArch(String v) { this.arch = v; }
    public Integer getDomestic() { return domestic; }
    public void setDomestic(Integer v) { this.domestic = v; }
    public String getCardModels() { return cardModels; }
    public void setCardModels(String v) { this.cardModels = v; }
    public Integer getNodeCount() { return nodeCount; }
    public void setNodeCount(Integer v) { this.nodeCount = v; }
    public String getAdaptStatus() { return adaptStatus; }
    public void setAdaptStatus(String v) { this.adaptStatus = v; }
    public BigDecimal getPerfRatio() { return perfRatio; }
    public void setPerfRatio(BigDecimal v) { this.perfRatio = v; }
    public BigDecimal getStabilityScore() { return stabilityScore; }
    public void setStabilityScore(BigDecimal v) { this.stabilityScore = v; }
    public BigDecimal getCostPerCardHour() { return costPerCardHour; }
    public void setCostPerCardHour(BigDecimal v) { this.costPerCardHour = v; }
    public String getSchedPolicy() { return schedPolicy; }
    public void setSchedPolicy(String v) { this.schedPolicy = v; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer v) { this.enabled = v; }
    public String getDiscoverMode() { return discoverMode; }
    public void setDiscoverMode(String v) { this.discoverMode = v; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String v) { this.updatedBy = v; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { this.updatedAt = v; }
}

package com.sunyard.llm.mas.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * x-mas-meta 扩展元信息（§4.3 / 附录 E.8）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MasMeta {

    @JsonProperty("cache_hit")
    private boolean cacheHit;

    @JsonProperty("cache_level")
    private String cacheLevel;

    @JsonProperty("routed_to")
    private String routedTo;

    @JsonProperty("pipeline_cost_ms")
    private long pipelineCostMs;

    @JsonProperty("intent")
    private String intent;

    @JsonProperty("difficulty")
    private Double difficulty;

    public boolean isCacheHit() { return cacheHit; }
    public void setCacheHit(boolean cacheHit) { this.cacheHit = cacheHit; }
    public String getCacheLevel() { return cacheLevel; }
    public void setCacheLevel(String cacheLevel) { this.cacheLevel = cacheLevel; }
    public String getRoutedTo() { return routedTo; }
    public void setRoutedTo(String routedTo) { this.routedTo = routedTo; }
    public long getPipelineCostMs() { return pipelineCostMs; }
    public void setPipelineCostMs(long pipelineCostMs) { this.pipelineCostMs = pipelineCostMs; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public Double getDifficulty() { return difficulty; }
    public void setDifficulty(Double difficulty) { this.difficulty = difficulty; }
}

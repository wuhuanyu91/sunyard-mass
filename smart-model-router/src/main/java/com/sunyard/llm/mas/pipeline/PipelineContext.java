package com.sunyard.llm.mas.pipeline;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.model.MasMeta;
import com.sunyard.llm.mas.service.ModelConfig;

/**
 * 流水线上下文（附录 G.8）：Stage 间传递 trace_id、用户身份、各层中间结果与耗时。
 */
public class PipelineContext {

    private final long startNanos = System.nanoTime();

    private String traceId;
    private String userId = "anonymous";
    private String appId;
    /** 请求体 OpenAI 标准 user 字段：每次调用自报的智能体/终端用户标识（审计用，非可信身份） */
    private String agentId;
    private String authorization;

    /** 可变请求 JSON（L4 压缩仅修改本副本，不影响缓存键计算用的原始请求） */
    private ObjectNode request;
    private String requestedModel = "";
    private boolean stream;

    private String cacheKey;
    private String cachedResponse;

    private String intent;
    private ModelConfig target;

    private int promptTokens;
    private int reservedTokens;

    /** 命中的限流规则（响应结束后据此归还并发额度） */
    private String rateLimitRuleId;
    /** 限流降级标记：命中 DOWNGRADE 动作时置位，L3 据此改用低成本模型 */
    private boolean downgraded;
    /** SLA 等级（P0-P3，来自应用画像）：P0 关键业务在资源紧张时优先保障 */
    private String slaLevel;
    /** 命中的灰度发布单号（写入 x-mas-meta 便于追溯） */
    private String grayReleaseId;

    private final MasMeta meta = new MasMeta();

    public long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    public long getStartNanos() { return startNanos; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getAuthorization() { return authorization; }
    public void setAuthorization(String authorization) { this.authorization = authorization; }
    public ObjectNode getRequest() { return request; }
    public void setRequest(ObjectNode request) { this.request = request; }
    public String getRequestedModel() { return requestedModel; }
    public void setRequestedModel(String requestedModel) { this.requestedModel = requestedModel; }
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
    public String getCacheKey() { return cacheKey; }
    public void setCacheKey(String cacheKey) { this.cacheKey = cacheKey; }
    public String getCachedResponse() { return cachedResponse; }
    public void setCachedResponse(String cachedResponse) { this.cachedResponse = cachedResponse; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public ModelConfig getTarget() { return target; }
    public void setTarget(ModelConfig target) { this.target = target; }
    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    public int getReservedTokens() { return reservedTokens; }
    public void setReservedTokens(int reservedTokens) { this.reservedTokens = reservedTokens; }
    public String getRateLimitRuleId() { return rateLimitRuleId; }
    public void setRateLimitRuleId(String rateLimitRuleId) { this.rateLimitRuleId = rateLimitRuleId; }
    public boolean isDowngraded() { return downgraded; }
    public void setDowngraded(boolean downgraded) { this.downgraded = downgraded; }
    public String getSlaLevel() { return slaLevel; }
    public void setSlaLevel(String slaLevel) { this.slaLevel = slaLevel; }
    public String getGrayReleaseId() { return grayReleaseId; }
    public void setGrayReleaseId(String grayReleaseId) { this.grayReleaseId = grayReleaseId; }
    public MasMeta getMeta() { return meta; }
}

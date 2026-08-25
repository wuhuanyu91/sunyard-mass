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
    public MasMeta getMeta() { return meta; }
}

package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.pipeline.stage.L2MultiLevelCacheStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * 请求转发器（§9.2 ForwardService / 附录 G.5）：
 * 非流式 bodyToMono；流式 bodyToFlux 逐条透传（禁止缓冲），
 * 透传同时聚合 content，完成后异步写两级缓存与调用记录。
 */
@Service
public class ForwardService {

    private static final Logger log = LoggerFactory.getLogger(ForwardService.class);

    private final WebClient webClient;
    private final MasProperties props;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final ExactCacheService exactCache;
    private final SemanticCacheService semanticCache;
    private final CallLogService callLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public ForwardService(WebClient masWebClient, MasProperties props,
                          SensitiveWordFilter sensitiveWordFilter,
                          ExactCacheService exactCache,
                          SemanticCacheService semanticCache,
                          CallLogService callLog) {
        this.webClient = masWebClient;
        this.props = props;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.exactCache = exactCache;
        this.semanticCache = semanticCache;
        this.callLog = callLog;
    }

    // ---------------- 非流式 ----------------

    public Mono<String> forwardNonStream(PipelineContext ctx) {
        ObjectNode body = backendBody(ctx, false);
        return webClient.post()
                .uri(chatUrl(ctx))
                .header("Content-Type", "application/json")
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(resp -> finalizeNonStream(ctx, resp))
                .onErrorMap(e -> e instanceof MasException ? e : mapUpstream(e));
    }

    private String finalizeNonStream(PipelineContext ctx, String upstreamResp) {
        ObjectNode root;
        try {
            root = (ObjectNode) mapper.readTree(upstreamResp);
        } catch (Exception e) {
            throw MasException.engineError("invalid JSON from engine");
        }
        if (root.has("error")) {
            throw MasException.engineError(root.path("error").path("message").asText("unknown"));
        }

        // L4 响应阶段：输出审核（非流式全文 AC 脱敏，附录 G.7）
        JsonNode message = root.path("choices").path(0).path("message");
        String original = message.path("content").asText("");
        String content = original;
        // 推理型后端（如 gpt-oss）可能把全部预算花在 reasoning 字段而 content 为空，兜底降级取 reasoning
        if (content.isEmpty() && message.isObject() && !message.path("reasoning").asText("").isEmpty()) {
            content = message.path("reasoning").asText("");
        }
        String masked = sensitiveWordFilter.mask(content);
        // 兜底降级或脱敏导致内容变化时写回 content
        if (message.isObject() && !masked.equals(original)) {
            ((ObjectNode) message).put("content", masked);
        }

        // Token 统计：usage 若后端已返回则直接透传，否则 jtokkit 近似计数（附录 E.6）
        int promptTokens = ctx.getPromptTokens();
        int completionTokens = TokenCounter.count(masked);
        int totalTokens = promptTokens + completionTokens;
        JsonNode usage = root.path("usage");
        if (usage.isMissingNode() || usage.isNull()) {
            ObjectNode u = root.putObject("usage");
            u.put("prompt_tokens", promptTokens);
            u.put("completion_tokens", completionTokens);
            u.put("total_tokens", totalTokens);
        } else {
            promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
            completionTokens = usage.path("completion_tokens").asInt(completionTokens);
            totalTokens = usage.path("total_tokens").asInt(totalTokens);
        }

        ctx.getMeta().setPipelineCostMs(ctx.elapsedMs());
        // 缓存写入不含 x-mas-meta（命中时会重生成 id/created，附录 G.4）
        writeCaches(ctx, root.toString());
        callLog.logAsync(ctx, promptTokens, completionTokens, totalTokens, ctx.elapsedMs(), true);

        root.putPOJO("x-mas-meta", ctx.getMeta());
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw MasException.internal("response serialize failed");
        }
    }

    // ---------------- 流式 ----------------

    public Flux<String> forwardStream(PipelineContext ctx) {
        ObjectNode body = backendBody(ctx, true);
        StringBuilder aggregated = new StringBuilder();
        StringBuilder reasoningBuf = new StringBuilder();
        return webClient.post()
                .uri(chatUrl(ctx))
                .header("Content-Type", "application/json")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToFlux(String.class)
                .filter(payload -> !"[DONE]".equals(payload.trim()))
                .doOnNext(payload -> appendDelta(aggregated, reasoningBuf, payload))
                .map(payload -> "data: " + payload + "\n\n")
                .concatWith(Flux.defer(() -> {
                    // 推理型后端 content 为空时兜底用 reasoning 聚合结果
                    String full = aggregated.length() > 0 ? aggregated.toString() : reasoningBuf.toString();
                    finalizeStream(ctx, full);
                    return Flux.just("data: " + metaChunkJson(ctx) + "\n\n", "data: [DONE]\n\n");
                }))
                .onErrorResume(e -> {
                    // 附录 G.5.4：中途断流追加 finish_reason=error chunk 后结束
                    log.warn("Stream broken mid-way: {}", e.getMessage());
                    String errChunk = "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"error\"}]}\n\n";
                    return Flux.just(errChunk);
                });
    }

    private void appendDelta(StringBuilder aggregated, StringBuilder reasoningBuf, String payload) {
        try {
            JsonNode delta = mapper.readTree(payload).path("choices").path(0).path("delta");
            JsonNode piece = delta.path("content");
            if (!piece.isMissingNode() && !piece.isNull()) {
                aggregated.append(piece.asText());
            }
            // 推理型后端的 reasoning 帧单独聚合，仅在 content 为空时作为兜底来源
            JsonNode reasoning = delta.path("reasoning");
            if (!reasoning.isMissingNode() && !reasoning.isNull()) {
                reasoningBuf.append(reasoning.asText());
            }
        } catch (Exception ignored) {
            // 非 JSON 行忽略
        }
    }

    private void finalizeStream(PipelineContext ctx, String fullContent) {
        ctx.getMeta().setPipelineCostMs(ctx.elapsedMs());
        // 流式输出审核：已输出内容不阻断，仅告警（附录 G.7）
        String masked = sensitiveWordFilter.mask(fullContent);
        if (!masked.equals(fullContent)) {
            log.warn("Stream output contains sensitive words (trace={}), not blocked", ctx.getTraceId());
        }
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", ""));
            root.put("object", "chat.completion");
            root.put("created", Instant.now().getEpochSecond());
            root.put("model", ctx.getRequestedModel());
            ObjectNode choice = root.putArray("choices").addObject();
            choice.put("index", 0);
            choice.putObject("message").put("role", "assistant").put("content", fullContent);
            choice.put("finish_reason", "stop");
            int promptTokens = ctx.getPromptTokens();
            int completionTokens = TokenCounter.count(fullContent);
            ObjectNode u = root.putObject("usage");
            u.put("prompt_tokens", promptTokens);
            u.put("completion_tokens", completionTokens);
            u.put("total_tokens", promptTokens + completionTokens);
            writeCaches(ctx, root.toString());
            callLog.logAsync(ctx, promptTokens, completionTokens,
                    promptTokens + completionTokens, ctx.elapsedMs(), true);
        } catch (Exception e) {
            log.warn("Stream finalize failed: {}", e.getMessage());
        }
    }

    private String metaChunkJson(PipelineContext ctx) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.putPOJO("x-mas-meta", ctx.getMeta());
        return chunk.toString();
    }

    // ---------------- 公共 ----------------

    private String chatUrl(PipelineContext ctx) {
        return ctx.getTarget().endpointUrl() + "/chat/completions";
    }

    private ObjectNode backendBody(PipelineContext ctx, boolean stream) {
        ObjectNode body = ctx.getRequest().deepCopy();
        String modelId = ctx.getTarget().modelId();
        String override = props.getBackend().getModelOverrides().get(modelId);
        body.put("model", override != null && !override.isBlank() ? override : modelId);
        body.put("stream", stream);
        return body;
    }

    /** 异步写精确 + 语义两级缓存（附录 G.4：失败仅告警不阻断） */
    private void writeCaches(PipelineContext ctx, String baseResponseJson) {
        String key = ctx.getCacheKey();
        if (key == null) {
            return;
        }
        String modelId = ctx.getRequestedModel();
        exactCache.put(key, modelId, baseResponseJson)
                .then(Mono.defer(() -> {
                    String text = L2MultiLevelCacheStage.lastUserText(ctx.getRequest());
                    if (text == null || text.isBlank()) {
                        return Mono.empty();
                    }
                    return semanticCache.embed(text)
                            .flatMap(vector -> semanticCache.put(modelId, vector, baseResponseJson));
                }))
                .subscribe(null, e -> log.warn("Cache write failed (skip): {}", e.getMessage()));
    }

    private MasException mapUpstream(Throwable e) {
        if (e instanceof TimeoutException) {
            return MasException.engineTimeout(e);
        }
        if (e instanceof WebClientResponseException wcre) {
            return MasException.engineError("HTTP " + wcre.getStatusCode().value());
        }
        return MasException.engineError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
    }
}

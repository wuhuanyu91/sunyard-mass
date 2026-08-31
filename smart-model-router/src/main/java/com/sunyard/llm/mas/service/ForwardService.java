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
    private final ModelRouter modelRouter;
    private final ModelHealthTracker healthTracker;
    private final QuotaService quotaService;
    private final ObjectMapper mapper = new ObjectMapper();

    public ForwardService(WebClient masWebClient, MasProperties props,
                          SensitiveWordFilter sensitiveWordFilter,
                          ExactCacheService exactCache,
                          SemanticCacheService semanticCache,
                          CallLogService callLog,
                          ModelRouter modelRouter,
                          ModelHealthTracker healthTracker,
                          QuotaService quotaService) {
        this.webClient = masWebClient;
        this.props = props;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.exactCache = exactCache;
        this.semanticCache = semanticCache;
        this.callLog = callLog;
        this.modelRouter = modelRouter;
        this.healthTracker = healthTracker;
        this.quotaService = quotaService;
    }

    // ---------------- 非流式 ----------------

    public Mono<String> forwardNonStream(PipelineContext ctx) {
        // 熔断器检查：当前目标不可用时尝试故障转移
        checkFailover(ctx);
        ObjectNode body = backendBody(ctx, false);
        return webClient.post()
                .uri(chatUrl(ctx))
                .header("Content-Type", "application/json")
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(resp -> {
                    healthTracker.recordSuccess(ctx.getTarget().endpointUrl());
                    return finalizeNonStream(ctx, resp);
                })
                .onErrorResume(e -> {
                    if (e instanceof MasException) {
                        return Mono.error(e);
                    }
                    healthTracker.recordFailure(ctx.getTarget().endpointUrl());
                    // 尝试故障转移重试一次
                    ModelConfig fallback = tryFailover(ctx);
                    if (fallback != null) {
                        log.info("Failover to model {} (trace={})", fallback.modelId(), ctx.getTraceId());
                        ObjectNode retryBody = backendBody(ctx, false);
                        return webClient.post()
                                .uri(fallback.endpointUrl() + "/chat/completions")
                                .header("Content-Type", "application/json")
                                .bodyValue(retryBody.toString())
                                .retrieve()
                                .bodyToMono(String.class)
                                .map(resp -> {
                                    healthTracker.recordSuccess(fallback.endpointUrl());
                                    return finalizeNonStream(ctx, resp);
                                })
                                .onErrorResume(re -> {
                                    if (re instanceof MasException) {
                                        return Mono.error(re);
                                    }
                                    healthTracker.recordFailure(fallback.endpointUrl());
                                    return Mono.error(mapUpstream(re));
                                });
                    }
                    return Mono.error(mapUpstream(e));
                });
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
        if (ctx.getReservedTokens() > 0) {
            quotaService.settle(ctx.getUserId(), ctx.getReservedTokens(), totalTokens)
                    .subscribe(null, e -> log.warn("Quota settle failed (skip): {}", e.getMessage()));
        }
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
        // 熔断器检查：当前目标不可用时尝试故障转移
        checkFailover(ctx);
        ObjectNode body = backendBody(ctx, true);
        StringBuilder aggregated = new StringBuilder();
        StringBuilder reasoningBuf = new StringBuilder();
        // §8 改进：流式输出敏感词阻断（滑动窗口审核）
        final boolean[] blocked = {false};
        final int WINDOW_SIZE = 5;
        return webClient.post()
                .uri(chatUrl(ctx))
                .header("Content-Type", "application/json")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body.toString())
                .retrieve()
                .bodyToFlux(String.class)
                .filter(payload -> !"[DONE]".equals(payload.trim()))
                .doOnNext(payload -> appendDelta(aggregated, reasoningBuf, payload))
                .doOnComplete(() -> healthTracker.recordSuccess(ctx.getTarget().endpointUrl()))
                // 滑动窗口审核：每 WINDOW_SIZE 个 chunk 检查一次敏感词
                .buffer(WINDOW_SIZE)
                .concatMap(batch -> {
                    if (blocked[0]) {
                        return Flux.empty();
                    }
                    // 聚合本窗口 content
                    StringBuilder windowContent = new StringBuilder();
                    for (String p : batch) {
                        try {
                            JsonNode delta = mapper.readTree(p).path("choices").path(0).path("delta");
                            String content = delta.path("content").asText("");
                            if (!content.isEmpty()) {
                                windowContent.append(content);
                            }
                        } catch (Exception ignored) {}
                    }
                    // 敏感词检查
                    String windowText = windowContent.toString();
                    if (!windowText.isEmpty() && sensitiveWordFilter.contains(windowText)) {
                        blocked[0] = true;
                        log.warn("Stream content blocked by sensitive word filter (trace={})", ctx.getTraceId());
                        ctx.getMeta().setContentBlocked(true);
                        // 发送阻断通知帧（合法 chunk 结构，严格 SDK 可解析）
                        ObjectNode blockChunk = chunkFrame(ctx, "[内容已被审核系统阻断]", "content_filter");
                        return Flux.just("data: " + blockChunk + "\n\n");
                    }
                    // 通过审核，正常透传本窗口
                    return Flux.fromIterable(batch).map(payload -> "data: " + payload + "\n\n");
                })
                .concatWith(Flux.defer(() -> {
                    String full = aggregated.length() > 0 ? aggregated.toString() : reasoningBuf.toString();
                    finalizeStream(ctx, full);
                    ObjectNode metaChunk = chunkFrame(ctx, "", null);
                    metaChunk.putPOJO("x-mas-meta", ctx.getMeta());
                    return Flux.just("data: " + metaChunk + "\n\n", "data: [DONE]\n\n");
                }))
                .onErrorResume(e -> {
                    healthTracker.recordFailure(ctx.getTarget().endpointUrl());
                    log.warn("Stream broken mid-way: {}", e.getMessage());
                    ObjectNode errChunk = chunkFrame(ctx, "", "stop");
                    errChunk.putPOJO("x-mas-meta", ctx.getMeta());
                    return Flux.just("data: " + errChunk + "\n\n");
                });
    }

    /** 构造合法的 chat.completion.chunk 帧（补全 id/object/created/model/choices），避免严格 SDK 解析失败 */
    private ObjectNode chunkFrame(PipelineContext ctx, String content, String finishReason) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.put("id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", ""));
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", Instant.now().getEpochSecond());
        chunk.put("model", ctx.getRequestedModel());
        ObjectNode c0 = chunk.putArray("choices").addObject();
        c0.put("index", 0);
        ObjectNode delta = c0.putObject("delta");
        if (!content.isEmpty()) {
            delta.put("content", content);
        }
        if (finishReason == null) {
            c0.putNull("finish_reason");
        } else {
            c0.put("finish_reason", finishReason);
        }
        return chunk;
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
        // §8 改进：流式输出审核 — 已在滑动窗口中阻断，此处仅作最终聚合告警
        Boolean isBlocked = ctx.getMeta().getContentBlocked();
        if (isBlocked == null || !isBlocked) {
            String masked = sensitiveWordFilter.mask(fullContent);
            if (!masked.equals(fullContent)) {
                log.warn("Stream output contains sensitive words (trace={}), post-window check", ctx.getTraceId());
            }
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
            if (isBlocked == null || !isBlocked) {
                writeCaches(ctx, root.toString());
            }
            int totalTokens = promptTokens + completionTokens;
            if (ctx.getReservedTokens() > 0) {
                quotaService.settle(ctx.getUserId(), ctx.getReservedTokens(), totalTokens)
                        .subscribe(null, e -> log.warn("Quota settle failed (skip): {}", e.getMessage()));
            }
            callLog.logAsync(ctx, promptTokens, completionTokens,
                    totalTokens, ctx.elapsedMs(), true);
        } catch (Exception e) {
            log.warn("Stream finalize failed: {}", e.getMessage());
        }
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

    // ---------------- 熔断器故障转移 ----------------

    /** 检查当前目标端点是否可用，不可用时替换为备用模型 */
    private void checkFailover(PipelineContext ctx) {
        if (ctx.getTarget() != null && !healthTracker.isAvailable(ctx.getTarget().endpointUrl())) {
            ModelConfig fallback = tryFailover(ctx);
            if (fallback != null) {
                log.info("Pre-call failover to model {} (trace={})", fallback.modelId(), ctx.getTraceId());
            }
        }
    }

    /** 尝试故障转移：找同意图的健康备用模型，返回 null 表示无备用 */
    private ModelConfig tryFailover(PipelineContext ctx) {
        String intent = ctx.getIntent();
        if (intent == null) {
            intent = "chat";
        }
        String excludeEndpoint = ctx.getTarget() != null ? ctx.getTarget().endpointUrl() : "";
        ModelConfig fallback = modelRouter.pickByIntentWithFallback(intent, excludeEndpoint);
        if (fallback != null) {
            ctx.setTarget(fallback);
            ctx.getMeta().setRoutedTo("failover:" + fallback.modelId());
            return fallback;
        }
        return null;
    }
}

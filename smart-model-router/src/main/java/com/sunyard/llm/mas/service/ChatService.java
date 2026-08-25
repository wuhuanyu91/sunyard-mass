package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.pipeline.RoutingPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 对话入口服务：编排流水线 + 缓存命中直返/回放 + 未命中转发。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int REPLAY_CHUNK_CHARS = 8;

    private final RoutingPipeline pipeline;
    private final ForwardService forwardService;
    private final CallLogService callLog;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatService(RoutingPipeline pipeline, ForwardService forwardService, CallLogService callLog) {
        this.pipeline = pipeline;
        this.forwardService = forwardService;
        this.callLog = callLog;
    }

    /** 非流式 */
    public Mono<String> execute(PipelineContext ctx) {
        return pipeline.process(ctx)
                .then(Mono.defer(() -> {
                    if (ctx.getCachedResponse() != null) {
                        return Mono.just(buildCachedResponse(ctx));
                    }
                    return forwardService.forwardNonStream(ctx);
                }));
    }

    /** 流式 */
    public Flux<String> executeStream(PipelineContext ctx) {
        return pipeline.process(ctx)
                .thenMany(Flux.defer(() -> {
                    if (ctx.getCachedResponse() != null) {
                        return replayCachedStream(ctx);
                    }
                    return forwardService.forwardStream(ctx);
                }));
    }

    // ---------------- 缓存命中路径 ----------------

    /** 命中返回：重生成 id/created，附加 x-mas-meta（附录 G.4） */
    private String buildCachedResponse(PipelineContext ctx) {
        ctx.getMeta().setPipelineCostMs(ctx.elapsedMs());
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(ctx.getCachedResponse());
            root.put("id", newCompletionId());
            root.put("created", Instant.now().getEpochSecond());
            root.putPOJO("x-mas-meta", ctx.getMeta());
            callLog.logAsync(ctx, 0, 0, 0, ctx.elapsedMs(), true);
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Cached response corrupted, fallback to forward: {}", e.getMessage());
            throw MasException.internal("cached response corrupted");
        }
    }

    /** 流式命中回放：完整响应拆为 chunk 序列按 SSE 输出（附录 G.5.3） */
    private Flux<String> replayCachedStream(PipelineContext ctx) {
        ctx.getMeta().setPipelineCostMs(ctx.elapsedMs());
        final String id = newCompletionId();
        final long created = Instant.now().getEpochSecond();
        final String model;
        final String content;
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(ctx.getCachedResponse());
            model = root.path("model").asText(ctx.getRequestedModel());
            content = root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            return Flux.error(MasException.internal("cached response corrupted"));
        }

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < content.length(); i += REPLAY_CHUNK_CHARS) {
            parts.add(content.substring(i, Math.min(i + REPLAY_CHUNK_CHARS, content.length())));
        }
        if (parts.isEmpty()) {
            parts.add("");
        }

        Flux<String> chunks = Flux.fromIterable(parts).map(part -> "data: " + chunkJson(id, created, model, part, false) + "\n\n");
        return chunks.concatWith(Flux.defer(() -> {
            callLog.logAsync(ctx, 0, 0, 0, ctx.elapsedMs(), true);
            ObjectNode metaChunk = mapper.createObjectNode();
            metaChunk.putPOJO("x-mas-meta", ctx.getMeta());
            return Flux.just(
                    "data: " + chunkJson(id, created, model, "", true) + "\n\n",
                    "data: " + metaChunk + "\n\n",
                    "data: [DONE]\n\n");
        }));
    }

    private String chunkJson(String id, long created, String model, String contentDelta, boolean finish) {
        ObjectNode chunk = mapper.createObjectNode();
        chunk.put("id", id);
        chunk.put("object", "chat.completion.chunk");
        chunk.put("created", created);
        chunk.put("model", model);
        ArrayNode choices = chunk.putArray("choices");
        ObjectNode c0 = choices.addObject();
        c0.put("index", 0);
        ObjectNode delta = c0.putObject("delta");
        if (!contentDelta.isEmpty()) {
            delta.put("content", contentDelta);
        }
        if (finish) {
            c0.put("finish_reason", "stop");
        } else {
            c0.putNull("finish_reason");
        }
        return chunk.toString();
    }

    private String newCompletionId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "");
    }
}

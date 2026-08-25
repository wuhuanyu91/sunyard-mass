package com.sunyard.llm.mas.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.service.CacheKeyGenerator;
import com.sunyard.llm.mas.service.ExactCacheService;
import com.sunyard.llm.mas.service.SemanticCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * L2 多级缓存层（§5.2 / 附录 G.4/G.7）：
 * 先 Caffeine/PG 精确缓存，未命中再走 pgvector 语义缓存（embedding + 余弦阈值 0.95）。
 * embedding 服务或 PG 异常时降级跳过，直接进 L3。
 */
@Component
public class L2MultiLevelCacheStage {

    private static final Logger log = LoggerFactory.getLogger(L2MultiLevelCacheStage.class);

    private final ExactCacheService exactCache;
    private final SemanticCacheService semanticCache;

    public L2MultiLevelCacheStage(ExactCacheService exactCache, SemanticCacheService semanticCache) {
        this.exactCache = exactCache;
        this.semanticCache = semanticCache;
    }

    public Mono<Void> lookup(PipelineContext ctx) {
        String key = CacheKeyGenerator.generate(ctx.getRequest());
        ctx.setCacheKey(key);
        String modelId = ctx.getRequestedModel();

        return exactCache.get(key)
                .map(hit -> {
                    markHit(ctx, hit, "exact");
                    return hit;
                })
                .switchIfEmpty(Mono.defer(() -> semanticLookup(ctx, modelId)
                        .map(hit -> {
                            markHit(ctx, hit, "semantic");
                            return hit;
                        })))
                .then()
                .onErrorResume(e -> {
                    log.warn("L2 cache lookup degraded (skip): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** 语义缓存检索：仅以最后一条 user message 做 embedding（附录 G.4） */
    private Mono<String> semanticLookup(PipelineContext ctx, String modelId) {
        String text = lastUserText(ctx.getRequest());
        if (text == null || text.isBlank()) {
            return Mono.empty();
        }
        return semanticCache.embed(text)
                .flatMap(vector -> semanticCache.search(modelId, vector))
                .filter(hit -> hit.similarity() >= semanticCache.threshold())
                .map(SemanticCacheService.SemanticHit::responseJson)
                .onErrorResume(e -> {
                    log.debug("Semantic cache lookup degraded (skip): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    private void markHit(PipelineContext ctx, String responseJson, String level) {
        ctx.setCachedResponse(responseJson);
        ctx.getMeta().setCacheHit(true);
        ctx.getMeta().setCacheLevel(level);
        ctx.getMeta().setRoutedTo("cache:" + level);
    }

    public static String lastUserText(JsonNode request) {
        JsonNode messages = request.path("messages");
        if (!messages.isArray()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode m = messages.get(i);
            if ("user".equals(m.path("role").asText())) {
                return m.path("content").asText("");
            }
        }
        return null;
    }
}

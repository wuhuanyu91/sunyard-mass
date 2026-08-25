package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.config.MasProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * L2.2 语义缓存（pgvector）+ embedding 调用（附录 G.4/G.7）。
 * embedding 服务不可用时由调用方降级跳过。
 */
@Service
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);

    private final DatabaseClient db;
    private final MasProperties props;
    private final WebClient webClient;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SemanticCacheService(DatabaseClient db, MasProperties props,
                                WebClient masWebClient, ModelRouter modelRouter) {
        this.db = db;
        this.props = props;
        this.webClient = masWebClient;
        this.modelRouter = modelRouter;
    }

    /** 调用 mas.cache.embedding-model 对应服务获取向量，格式化为 pgvector 文本 [..] */
    public Mono<String> embed(String text) {
        ModelConfig embeddingCfg = modelRouter.getModel(props.getCache().getEmbeddingModel());
        if (embeddingCfg == null || !embeddingCfg.active()) {
            return Mono.error(new IllegalStateException(
                    "embedding model not registered: " + props.getCache().getEmbeddingModel()));
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", embeddingCfg.modelId());
        body.put("input", text);
        return webClient.post()
                .uri(embeddingCfg.endpointUrl() + "/embeddings")
                .header("Content-Type", "application/json")
                .bodyValue(body.toString())
                .retrieve()
                .bodyToMono(String.class)
                .map(resp -> {
                    try {
                        JsonNode root = objectMapper.readTree(resp);
                        JsonNode vec = root.path("data").path(0).path("embedding");
                        if (!vec.isArray() || vec.isEmpty()) {
                            throw new IllegalStateException("empty embedding in response");
                        }
                        StringBuilder sb = new StringBuilder("[");
                        for (int i = 0; i < vec.size(); i++) {
                            if (i > 0) {
                                sb.append(',');
                            }
                            sb.append(vec.get(i).asDouble());
                        }
                        return sb.append(']').toString();
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to parse embedding response", e);
                    }
                });
    }

    /** 余弦检索 Top1，返回 (responseJson, similarity)；无结果返回 empty */
    public Mono<SemanticHit> search(String modelId, String vectorText) {
        return db.sql("""
                        SELECT response_json, 1 - (embedding <=> CAST(:v AS vector)) AS similarity
                        FROM mas_semantic_cache
                        WHERE model_id = :m AND expire_at > now()
                        ORDER BY embedding <=> CAST(:v AS vector) ASC
                        LIMIT 1
                        """)
                .bind("v", vectorText)
                .bind("m", modelId)
                .map(row -> new SemanticHit(
                        row.get("response_json", String.class),
                        toDouble(row.get("similarity"))))
                .first();
    }

    public Mono<Void> put(String modelId, String vectorText, String responseJson) {
        return db.sql("""
                        INSERT INTO mas_semantic_cache (model_id, embedding, response_json, expire_at)
                        VALUES (:m, CAST(:v AS vector), :r, now() + CAST(:ttl || ' seconds' AS INTERVAL))
                        """)
                .bind("m", modelId == null ? "" : modelId)
                .bind("v", vectorText)
                .bind("r", responseJson)
                .bind("ttl", String.valueOf(props.getCache().getSemanticTtl().toSeconds()))
                .then()
                .onErrorResume(e -> {
                    log.warn("Semantic cache write degraded (skip): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    /** 清空全部语义缓存条目（测试/管理端点使用） */
    public Mono<Void> flush() {
        return db.sql("DELETE FROM mas_semantic_cache")
                .fetch().rowsUpdated()
                .then()
                .onErrorResume(e -> {
                    log.warn("Semantic cache flush degraded (skip): {}", e.getMessage());
                    return Mono.empty();
                });
    }

    public double threshold() {
        return props.getCache().getSemanticThreshold();
    }

    /** 定时清理过期行 */
    @Scheduled(fixedDelay = 600_000)
    public void purgeExpired() {
        db.sql("DELETE FROM mas_semantic_cache WHERE expire_at < now()")
                .fetch().rowsUpdated()
                .subscribe(n -> log.debug("Semantic cache purged {} rows", n),
                        e -> log.warn("Semantic cache purge failed: {}", e.getMessage()));
    }

    private static double toDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0.0;
    }

    public record SemanticHit(String responseJson, double similarity) {
    }
}

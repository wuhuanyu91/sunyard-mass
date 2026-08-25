package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.ExactCacheService;
import com.sunyard.llm.mas.service.SemanticCacheService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 缓存管理端点（§10 管理面预览）：POST /smart-router/internal/cache/flush
 * 同时清空精确缓存（本地 Caffeine + PG）与语义缓存（PG），
 * 供测试套件保证确定性，后续管理面前端可直接复用。
 */
@RestController
public class CacheAdminController {

    private final ExactCacheService exactCache;
    private final SemanticCacheService semanticCache;

    public CacheAdminController(ExactCacheService exactCache, SemanticCacheService semanticCache) {
        this.exactCache = exactCache;
        this.semanticCache = semanticCache;
    }

    @PostMapping("/internal/cache/flush")
    public Mono<Map<String, Object>> flush() {
        return exactCache.flush()
                .then(semanticCache.flush())
                .thenReturn(Map.of("flushed", "exact+semantic"));
    }
}

package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.ApiKeyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * API Key 管理端点（§8 已知限制消除 — 鉴权体系）：
 * POST /internal/api-keys — 创建 API Key（返回明文 Key 一次）
 * DELETE /internal/api-keys — 撤销 API Key（按前缀）
 */
@RestController
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping("/internal/api-keys")
    public Mono<Map<String, Object>> createKey(@RequestBody Map<String, String> body) {
        String userId = body.getOrDefault("user_id", "anonymous");
        String appId = body.get("app_id");
        return apiKeyService.createKey(userId, appId)
                .map(plainKey -> Map.<String, Object>of(
                        "key", plainKey,
                        "user_id", userId,
                        "message", "Save this key, it will not be shown again"));
    }

    @DeleteMapping("/internal/api-keys")
    public Mono<Map<String, Object>> revokeKey(@RequestParam("prefix") String prefix) {
        return apiKeyService.revokeKey(prefix)
                .map(revoked -> Map.<String, Object>of(
                        "revoked", revoked,
                        "prefix", prefix));
    }
}

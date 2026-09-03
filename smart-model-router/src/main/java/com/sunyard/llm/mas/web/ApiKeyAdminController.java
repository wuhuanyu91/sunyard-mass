package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.service.ApiKeyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * API Key 管理端点（§8 鉴权体系 + §1.4 自助申请增强）：
 * <p>
 * 已有端点（兼容旧调用）：
 * <ul>
 *   <li>POST /internal/api-keys — 创建 API Key（返回明文 Key 一次）</li>
 *   <li>DELETE /internal/api-keys — 撤销 API Key（按前缀）</li>
 * </ul>
 * §1.4 新增端点：
 * <ul>
 *   <li>GET /internal/api-keys — 按条件筛选 Key 列表</li>
 *   <li>POST /internal/api-keys/{prefix}/rotate — Key 轮换</li>
 *   <li>POST /internal/api-keys/batch-revoke — 批量吊销</li>
 *   <li>GET /internal/api-keys/{prefix}/usage — 用量查询</li>
 * </ul>
 */
@RestController
public class ApiKeyAdminController {

    private final ApiKeyService apiKeyService;

    public ApiKeyAdminController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    /**
     * 创建 API Key（§1.4 增强：支持完整元数据 + 配额自动关联）。
     * 兼容旧调用：仅传 user_id / app_id 亦可。
     */
    @PostMapping("/internal/api-keys")
    public Mono<Map<String, Object>> createKey(@RequestBody Map<String, Object> body) {
        String userId = strOr(body, "user_id", "anonymous");
        String appId = strOrNull(body, "app_id");
        String teamName = strOrNull(body, "team_name");
        String agentName = strOrNull(body, "agent_name");
        String agentType = strOrNull(body, "agent_type");
        String purpose = strOrNull(body, "purpose");
        String quotaTier = strOr(body, "quota_tier", "default");
        String createdBy = strOr(body, "created_by", "admin");
        Integer expireDays = intOrNull(body, "expire_days");

        return apiKeyService.createKeyFull(userId, appId, teamName, agentName, agentType,
                        purpose, expireDays, quotaTier, createdBy)
                .map(plainKey -> Map.<String, Object>of(
                        "key", plainKey,
                        "user_id", userId,
                        "quota_tier", quotaTier,
                        "message", "Save this key, it will not be shown again"));
    }

    @DeleteMapping("/internal/api-keys")
    public Mono<Map<String, Object>> revokeKey(@RequestParam("prefix") String prefix) {
        return apiKeyService.revokeKey(prefix)
                .map(revoked -> Map.<String, Object>of(
                        "revoked", revoked,
                        "prefix", prefix));
    }

    /** §1.4 Key 列表查询 */
    @GetMapping("/internal/api-keys")
    public Mono<Map<String, Object>> listKeys(
            @RequestParam(value = "team_name", required = false) String teamName,
            @RequestParam(value = "user_id", required = false) String userId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "agent_type", required = false) String agentType) {
        return apiKeyService.listKeys(teamName, userId, status, agentType)
                .map(keys -> Map.<String, Object>of(
                        "keys", keys,
                        "total", keys.size()));
    }

    /** §1.4 Key 轮换：生成新 Key，旧 Key 进入 grace period */
    @PostMapping("/internal/api-keys/{prefix}/rotate")
    public Mono<Map<String, Object>> rotateKey(@PathVariable("prefix") String prefix) {
        return apiKeyService.rotateKey(prefix)
                .map(newKey -> Map.<String, Object>of(
                        "key", newKey,
                        "old_prefix", prefix,
                        "message", "New key created. Old key enters grace period. Save the new key, it will not be shown again"));
    }

    /** §1.4 批量吊销 */
    @PostMapping("/internal/api-keys/batch-revoke")
    public Mono<Map<String, Object>> batchRevoke(@RequestBody Map<String, String> body) {
        String teamName = body.get("team_name");
        String userId = body.get("user_id");
        return apiKeyService.batchRevoke(teamName, userId)
                .map(revoked -> Map.<String, Object>of(
                        "revoked_count", revoked,
                        "team_name", teamName != null ? teamName : "",
                        "user_id", userId != null ? userId : ""));
    }

    /** §1.4 用量查询 */
    @GetMapping("/internal/api-keys/{prefix}/usage")
    public Mono<Map<String, Object>> getUsage(@PathVariable("prefix") String prefix) {
        // 先查 Key 获取 user_id，再查用量
        return apiKeyService.listKeys(null, null, null, null)
                .flatMap(keys -> {
                    String userId = keys.stream()
                            .filter(k -> prefix.equals(k.get("key_prefix")))
                            .map(k -> (String) k.get("user_id"))
                            .findFirst()
                            .orElse(null);
                    if (userId == null) {
                        return Mono.just(Map.<String, Object>of("error", "key not found: " + prefix));
                    }
                    return apiKeyService.getUsage(userId);
                });
    }

    // ---- 工具方法 ----

    private static String strOr(Map<String, Object> body, String key, String defaultVal) {
        Object v = body.get(key);
        return v != null ? v.toString() : defaultVal;
    }

    private static String strOrNull(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v != null ? v.toString() : null;
    }

    private static Integer intOrNull(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

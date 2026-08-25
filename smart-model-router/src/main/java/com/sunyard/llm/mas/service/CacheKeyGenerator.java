package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 缓存键生成（附录 G.4，写死规则）：
 * cache_key = SHA256(model + "\n" + jsonMessages + "\n" + temperature + "\n" + maxTokens + "\n" + stream)
 * - messages 按请求原始顺序（不排序）序列化为紧凑 JSON，每条仅保留 role/content
 * - stream 字段现已包含在键中；temperature/max_tokens/stream 缺省以 "null" 占位
 */
public final class CacheKeyGenerator {

    private CacheKeyGenerator() {
    }

    public static String generate(JsonNode request) {
        StringBuilder sb = new StringBuilder();
        sb.append(textOrNull(request.path("model"))).append('\n');

        ArrayNode messages = JsonNodeFactory.instance.arrayNode();
        JsonNode msgs = request.path("messages");
        if (msgs.isArray()) {
            for (JsonNode m : msgs) {
                ObjectNode item = JsonNodeFactory.instance.objectNode();
                item.put("role", m.path("role").asText(""));
                item.put("content", m.path("content").asText(""));
                messages.add(item);
            }
        }
        sb.append(messages.toString()).append('\n');

        sb.append(numberOrNull(request.path("temperature"))).append('\n');
        sb.append(numberOrNull(request.path("max_tokens"))).append('\n');
        sb.append(booleanOrNull(request.path("stream")));

        return sha256Hex(sb.toString());
    }

    private static String textOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "null" : node.asText();
    }

    private static String numberOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "null" : node.asText();
    }

    private static String booleanOrNull(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? "null" : String.valueOf(node.asBoolean());
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Token 近似计数（附录 E.6：jtokkit）。
 */
public final class TokenCounter {

    private static final EncodingRegistry REGISTRY = Encodings.newDefaultEncodingRegistry();
    private static final Encoding ENCODING = REGISTRY.getEncoding(EncodingType.CL100K_BASE);

    private TokenCounter() {
    }

    public static int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return ENCODING.countTokens(text);
    }

    /** 统计 messages 的 token 数（role + content 文本，每条消息附加少量开销） */
    public static int countMessages(JsonNode messages) {
        if (messages == null || !messages.isArray()) {
            return 0;
        }
        int total = 0;
        for (JsonNode m : messages) {
            total += count(m.path("role").asText("")) + count(m.path("content").asText("")) + 4;
        }
        return total;
    }
}

package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token 计数边界测试：null/空串、纯空白、确定性、长文本单调性、
 * messages 各种非法/缺失形态、每条消息固定开销。
 */
class TokenCounterBoundaryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode node(String json) throws Exception {
        return MAPPER.readTree(json);
    }

    // ---- count(String) 边界 ----

    @Test
    void nullReturnsZero() {
        assertEquals(0, TokenCounter.count(null));
    }

    @Test
    void emptyStringReturnsZero() {
        assertEquals(0, TokenCounter.count(""));
    }

    @Test
    void whitespaceOnlyCountsPositive() {
        // 空白字符本身也是 token
        assertTrue(TokenCounter.count("   ") > 0);
        assertTrue(TokenCounter.count("\n\t") > 0);
    }

    @Test
    void countingIsDeterministic() {
        String text = "智能模型路由 MAS pipeline 测试";
        assertEquals(TokenCounter.count(text), TokenCounter.count(text));
    }

    @Test
    void longerTextCountsMoreTokens() {
        String shortText = "hello";
        String longText = "hello ".repeat(200);
        assertTrue(TokenCounter.count(longText) > TokenCounter.count(shortText));
    }

    @Test
    void differentTextsProduceDifferentCounts() {
        assertNotEquals(TokenCounter.count("a"), TokenCounter.count("a ".repeat(50)));
    }

    // ---- countMessages(JsonNode) 边界 ----

    @Test
    void nullMessagesReturnsZero() {
        assertEquals(0, TokenCounter.countMessages(null));
    }

    @Test
    void nonArrayMessagesReturnsZero() throws Exception {
        assertEquals(0, TokenCounter.countMessages(node("{\"a\":1}")));
        assertEquals(0, TokenCounter.countMessages(node("\"text\"")));
        assertEquals(0, TokenCounter.countMessages(node("123")));
    }

    @Test
    void emptyArrayReturnsZero() throws Exception {
        assertEquals(0, TokenCounter.countMessages(node("[]")));
    }

    @Test
    void eachMessageCarriesFixedOverheadOfFour() throws Exception {
        // 空消息：role/content 均为空串，仅剩每条 4 的开销
        assertEquals(4, TokenCounter.countMessages(node("[{\"role\":\"\",\"content\":\"\"}]")));
        assertEquals(12, TokenCounter.countMessages(node("[{},{},{}]")),
                "three empty messages = 3 x 4");
    }

    @Test
    void missingRoleAndContentStillCountOverhead() throws Exception {
        // 字段缺失按空串处理，仍产生 4 开销
        assertEquals(4, TokenCounter.countMessages(node("[{\"other\":1}]")));
    }

    @Test
    void totalEqualsRolePlusContentPlusOverhead() throws Exception {
        JsonNode messages = node("[{\"role\":\"user\",\"content\":\"你好，世界\"}]");
        int expected = TokenCounter.count("user") + TokenCounter.count("你好，世界") + 4;
        assertEquals(expected, TokenCounter.countMessages(messages));
    }
}

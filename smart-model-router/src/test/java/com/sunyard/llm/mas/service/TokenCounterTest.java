package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * jtokkit token 近似计数测试（附录 E.6）：配额预扣与 usage 兜底统计的基础。
 */
class TokenCounterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void nullAndEmptyCountZero() {
        assertEquals(0, TokenCounter.count(null));
        assertEquals(0, TokenCounter.count(""));
    }

    @Test
    void englishTextCountsPositive() {
        assertTrue(TokenCounter.count("hello world") > 0);
        // 更长文本 token 数单调不减
        assertTrue(TokenCounter.count("hello world, this is a longer sentence for counting")
                > TokenCounter.count("hello world"));
    }

    @Test
    void chineseTextCountsPositive() {
        assertTrue(TokenCounter.count("智能模型路由访问平台") > 0);
    }

    @Test
    void countMessagesSumsRolesAndContents() throws Exception {
        var messages = mapper.readTree("""
                [{"role":"user","content":"hello"},{"role":"assistant","content":"hi there"}]
                """);
        int expected = TokenCounter.count("user") + TokenCounter.count("hello") + 4
                + TokenCounter.count("assistant") + TokenCounter.count("hi there") + 4;
        assertEquals(expected, TokenCounter.countMessages(messages));
    }

    @Test
    void countMessagesHandlesInvalidInput() throws Exception {
        assertEquals(0, TokenCounter.countMessages(null));
        assertEquals(0, TokenCounter.countMessages(mapper.readTree("{\"not\":\"array\"}")));
        // 缺 content 的消息只计 role + 开销，不抛异常
        assertTrue(TokenCounter.countMessages(mapper.readTree("[{\"role\":\"user\"}]")) > 0);
    }
}

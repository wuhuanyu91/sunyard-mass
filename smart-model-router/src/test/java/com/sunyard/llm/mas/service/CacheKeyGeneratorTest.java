package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 附录 G.4 缓存键规则回归：
 * 键 = SHA256(model + "\n" + 紧凑messages(仅role/content、原顺序) + "\n" + temperature + "\n" + max_tokens)，stream 排除在外。
 */
class CacheKeyGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode baseRequest() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"model":"qwen-72b",
                 "messages":[{"role":"user","content":"什么是机器学习？"}],
                 "temperature":0.7,"max_tokens":100,"stream":false}
                """);
    }

    @Test
    void sameRequestProducesSameKey() throws Exception {
        assertEquals(CacheKeyGenerator.generate(baseRequest()), CacheKeyGenerator.generate(baseRequest()));
    }

    @Test
    void streamFlagExcludedFromKey() throws Exception {
        ObjectNode a = baseRequest();
        ObjectNode b = baseRequest();
        b.put("stream", true);
        assertEquals(CacheKeyGenerator.generate(a), CacheKeyGenerator.generate(b));
    }

    @Test
    void extraMessageFieldsIgnored() throws Exception {
        ObjectNode a = baseRequest();
        ObjectNode b = baseRequest();
        ((ObjectNode) b.path("messages").get(0)).put("name", "extra-field");
        assertEquals(CacheKeyGenerator.generate(a), CacheKeyGenerator.generate(b));
    }

    @Test
    void messageOrderMatters() throws Exception {
        ObjectNode a = baseRequest();
        ObjectNode b = (ObjectNode) mapper.readTree("""
                {"model":"qwen-72b",
                 "messages":[{"role":"assistant","content":"回答"},{"role":"user","content":"什么是机器学习？"}],
                 "temperature":0.7,"max_tokens":100}
                """);
        assertNotEquals(CacheKeyGenerator.generate(a), CacheKeyGenerator.generate(b));
    }

    @Test
    void missingParamsUseNullPlaceholder() throws Exception {
        ObjectNode a = baseRequest();
        a.remove("temperature");
        a.remove("max_tokens");
        ObjectNode b = baseRequest();
        b.putNull("temperature");
        b.putNull("max_tokens");
        // 缺省与显式 null 等价，均以 "null" 占位
        assertEquals(CacheKeyGenerator.generate(a), CacheKeyGenerator.generate(b));
        assertNotEquals(CacheKeyGenerator.generate(a), CacheKeyGenerator.generate(baseRequest()));
    }

    @Test
    void differentModelProducesDifferentKey() throws Exception {
        ObjectNode a = baseRequest();
        ObjectNode b = baseRequest();
        b.put("model", "deepseek-v3");
        assertNotEquals(CacheKeyGenerator.generate(a), CacheKeyGenerator.generate(b));
    }
}

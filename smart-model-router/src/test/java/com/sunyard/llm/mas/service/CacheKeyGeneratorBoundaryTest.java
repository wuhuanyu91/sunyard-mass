package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 缓存键生成边界测试（附录 G.4 写死规则）：SHA-256 十六进制格式、
 * 确定性、采样参数差异、messages 缺失/非数组形态、model 显式 null 等价缺省。
 */
class CacheKeyGeneratorBoundaryTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String key(String json) throws Exception {
        return CacheKeyGenerator.generate(MAPPER.readTree(json));
    }

    @Test
    void keyIs64CharLowercaseHex() throws Exception {
        String k = key("{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":\"x\"}]}");
        assertEquals(64, k.length());
        assertTrue(k.matches("[0-9a-f]{64}"), "expected sha256 hex, got: " + k);
    }

    @Test
    void deterministicAcrossInvocations() throws Exception {
        String json = "{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":\"同一请求\"}],\"temperature\":0.7}";
        assertEquals(key(json), key(json));
    }

    @Test
    void differentTemperatureProducesDifferentKey() throws Exception {
        String base = "{\"model\":\"m\",\"messages\":[],\"temperature\":%s}";
        assertNotEquals(key(base.formatted("0.7")), key(base.formatted("0.8")));
    }

    @Test
    void differentMaxTokensProducesDifferentKey() throws Exception {
        String base = "{\"model\":\"m\",\"messages\":[],\"max_tokens\":%s}";
        assertNotEquals(key(base.formatted("16")), key(base.formatted("32")));
    }

    @Test
    void missingMessagesEqualsEmptyArray() throws Exception {
        // messages 缺失与非数组都归一为空数组占位
        assertEquals(key("{\"model\":\"m\"}"), key("{\"model\":\"m\",\"messages\":[]}"));
        assertEquals(key("{\"model\":\"m\"}"), key("{\"model\":\"m\",\"messages\":\"oops\"}"));
    }

    @Test
    void explicitNullModelEqualsMissingModel() throws Exception {
        // textOrNull：缺失与显式 null 均以 "null" 占位
        assertEquals(key("{\"messages\":[]}"), key("{\"model\":null,\"messages\":[]}"));
    }

    @Test
    void unicodeContentIsStableAndDistinct() throws Exception {
        String base = "{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}";
        assertEquals(key(base.formatted("中文内容")), key(base.formatted("中文内容")));
        assertNotEquals(key(base.formatted("中文内容")), key(base.formatted("中文内容！")));
    }

    @Test
    void extraMessageFieldsDoNotAffectKeyButOrderDoes() throws Exception {
        // 附加字段被剥离：与仅含 role/content 的键一致
        assertEquals(
                key("{\"messages\":[{\"role\":\"user\",\"content\":\"a\"}]}"),
                key("{\"messages\":[{\"role\":\"user\",\"content\":\"a\",\"name\":\"x\",\"extra\":1}]}"));
        // 顺序敏感
        assertNotEquals(
                key("{\"messages\":[{\"role\":\"user\",\"content\":\"a\"},{\"role\":\"user\",\"content\":\"b\"}]}"),
                key("{\"messages\":[{\"role\":\"user\",\"content\":\"b\"},{\"role\":\"user\",\"content\":\"a\"}]}"));
    }
}

package com.sunyard.llm.mas.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附录 G.3 旧协议字段映射回归（§8 改进：补全参数 + 支持流式）。
 */
class LegacyCompatMappingTest {

    private final LegacyCompatController controller = new LegacyCompatController(null);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void requestMapping() throws Exception {
        ObjectNode req = controller.mapRequest("""
                {"modelId":"qwen-72b",
                 "messages":[{"role":"user","content":"你好"}],
                 "temperature":0.5,"maxTokens":64,"stream":true}
                """);
        assertEquals("qwen-72b", req.path("model").asText());
        assertEquals("你好", req.path("messages").path(0).path("content").asText());
        assertEquals(0.5, req.path("temperature").asDouble());
        assertEquals(64, req.path("max_tokens").asInt());
        // §8 改进：支持流式，stream 字段透传
        assertTrue(req.path("stream").asBoolean());
    }

    @Test
    void requestMappingStreamDefaultsToFalse() throws Exception {
        ObjectNode req = controller.mapRequest("""
                {"messages":[{"role":"user","content":"你好"}]}
                """);
        // 未指定 stream 时默认 false
        assertFalse(req.path("stream").asBoolean());
    }

    @Test
    void missingModelIdFallsBackToDefaultRouting() throws Exception {
        ObjectNode req = controller.mapRequest("""
                {"messages":[{"role":"user","content":"你好"}]}
                """);
        assertTrue(req.path("model").isMissingNode());
    }

    @Test
    void invalidJsonRejected() {
        assertThrows(RuntimeException.class, () -> controller.mapRequest("not-json"));
    }

    @Test
    void responseMapping() throws Exception {
        String legacy = controller.mapResponse("""
                {"id":"chatcmpl-abc","model":"qwen-72b",
                 "choices":[{"message":{"role":"assistant","content":"回复内容"}}],
                 "usage":{"prompt_tokens":5,"completion_tokens":3,"total_tokens":8}}
                """);
        JsonNode root = mapper.readTree(legacy);
        assertEquals(0, root.path("code").asInt());
        assertEquals("success", root.path("message").asText());
        JsonNode data = root.path("data");
        assertEquals("回复内容", data.path("content").asText());
        assertEquals("chatcmpl-abc", data.path("requestId").asText());
        assertEquals("qwen-72b", data.path("modelId").asText());
        assertEquals(8, data.path("usage").path("total_tokens").asInt());
    }
}

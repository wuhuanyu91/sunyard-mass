package com.sunyard.llm.mas.pipeline;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.exception.MasException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流水线上下文构建边界测试：stream 字段类型强转、model 缺失/空串/显式 null、
 * 非对象 JSON 报文拒绝、空白用户头归匿名。
 */
class PipelineContextFactoryBoundaryTest {

    private ServerWebExchange exchange(MockServerHttpRequest request) {
        return MockServerWebExchange.from(request);
    }

    private ObjectNode body() {
        return PipelineContextFactory.mapper().createObjectNode();
    }

    @Test
    void streamDefaultsToFalseWhenAbsent() {
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/x").build());
        PipelineContext ctx = PipelineContextFactory.create(ex, body());
        assertFalse(ctx.isStream());
    }

    @Test
    void streamExplicitFalseRespected() {
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/x").build());
        ObjectNode b = body();
        b.put("stream", false);
        assertFalse(PipelineContextFactory.create(ex, b).isStream());
    }

    @Test
    void streamStringTrueCoercedToBoolean() {
        // TextNode.asBoolean：字面量 "true" 强转为 true（宽松解析的边界行为）
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/x").build());
        ObjectNode b = body();
        b.put("stream", "true");
        assertTrue(PipelineContextFactory.create(ex, b).isStream());
    }

    @Test
    void emptyModelStringLeavesRoutingToPipeline() {
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/x").build());
        ObjectNode b = body();
        b.put("model", "");
        assertEquals("", PipelineContextFactory.create(ex, b).getRequestedModel());
    }

    @Test
    void jsonArrayBodyRejectedWithInvalidParam() {
        // readTree 成功但根节点非对象：cast 失败同样归一为 invalid_param
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/x").build());
        MasException e = assertThrows(MasException.class,
                () -> PipelineContextFactory.create(ex, "[1,2,3]"));
        assertEquals("invalid_param", e.getCode());
    }

    @Test
    void emptyBodyRejectedWithInvalidParam() {
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/x").build());
        MasException e = assertThrows(MasException.class,
                () -> PipelineContextFactory.create(ex, ""));
        assertEquals("invalid_param", e.getCode());
    }

    @Test
    void whitespaceOnlyUserIdTreatedAsAnonymous() {
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/x")
                .header("X-User-Id", "   ")
                .build());
        assertEquals("anonymous", PipelineContextFactory.create(ex, body()).getUserId());
    }
}

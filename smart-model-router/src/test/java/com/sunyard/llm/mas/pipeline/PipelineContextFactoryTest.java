package com.sunyard.llm.mas.pipeline;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.web.TraceWebFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流水线上下文构建测试（附录 G.1 身份契约）：
 * 请求头解析、model/stream 提取、非法 JSON 拒绝、匿名兜底。
 */
class PipelineContextFactoryTest {

    private ServerWebExchange exchange(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(TraceWebFilter.TRACE_ID_ATTR, "trace-001");
        return exchange;
    }

    @Test
    void parsesIdentityHeadersAndModel() {
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/v1/chat/completions")
                .header("X-User-Id", "alice")
                .header("X-App-Id", "crm")
                .header(HttpHeaders.AUTHORIZATION, "Bearer sk-test")
                .build());
        ObjectNode body = PipelineContextFactory.mapper().createObjectNode();
        body.put("model", "qwen-72b");
        body.put("stream", true);

        PipelineContext ctx = PipelineContextFactory.create(ex, body);
        assertEquals("alice", ctx.getUserId());
        assertEquals("crm", ctx.getAppId());
        assertEquals("Bearer sk-test", ctx.getAuthorization());
        assertEquals("trace-001", ctx.getTraceId());
        assertEquals("qwen-72b", ctx.getRequestedModel());
        assertTrue(ctx.isStream());
    }

    @Test
    void missingHeadersFallBackToAnonymous() {
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/v1/chat/completions").build());
        PipelineContext ctx = PipelineContextFactory.create(ex, PipelineContextFactory.mapper().createObjectNode());
        assertEquals("anonymous", ctx.getUserId());
        assertNull(ctx.getAppId());
        assertEquals("", ctx.getRequestedModel());
        assertFalse(ctx.isStream());
    }

    @Test
    void invalidJsonRejectedWithInvalidParam() {
        ServerWebExchange ex = exchange(MockServerHttpRequest.post("/v1/chat/completions").build());
        MasException e = assertThrows(MasException.class,
                () -> PipelineContextFactory.create(ex, "this is not json"));
        assertEquals("invalid_param", e.getCode());
    }

    @Test
    void missingTraceAttributeYieldsEmptyTraceId() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.post("/v1/chat/completions").build());
        PipelineContext ctx = PipelineContextFactory.create(ex, PipelineContextFactory.mapper().createObjectNode());
        assertEquals("", ctx.getTraceId());
    }
}

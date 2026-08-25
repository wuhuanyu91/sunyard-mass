package com.sunyard.llm.mas.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.web.TraceWebFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

/**
 * 从 HTTP 交换构建流水线上下文（附录 G.1 身份契约：X-User-Id / X-App-Id / Authorization）。
 */
public final class PipelineContextFactory {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PipelineContextFactory() {
    }

    public static PipelineContext create(ServerWebExchange exchange, String rawBody) {
        ObjectNode request;
        try {
            request = (ObjectNode) MAPPER.readTree(rawBody);
        } catch (Exception e) {
            throw MasException.invalidParam("request body is not valid JSON");
        }
        return create(exchange, request);
    }

    public static PipelineContext create(ServerWebExchange exchange, ObjectNode request) {
        PipelineContext ctx = new PipelineContext();
        Object trace = exchange.getAttribute(TraceWebFilter.TRACE_ID_ATTR);
        ctx.setTraceId(trace == null ? "" : trace.toString());

        HttpHeaders headers = exchange.getRequest().getHeaders();
        String userId = headers.getFirst("X-User-Id");
        ctx.setUserId(userId == null || userId.isBlank() ? "anonymous" : userId);
        ctx.setAppId(headers.getFirst("X-App-Id"));
        ctx.setAuthorization(headers.getFirst(HttpHeaders.AUTHORIZATION));

        ctx.setRequest(request);
        ctx.setRequestedModel(request.path("model").asText(""));
        ctx.setStream(request.path("stream").asBoolean(false));
        return ctx;
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}

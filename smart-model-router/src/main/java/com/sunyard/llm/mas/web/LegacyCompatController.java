package com.sunyard.llm.mas.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.pipeline.PipelineContextFactory;
import com.sunyard.llm.mas.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 旧协议兼容层（§4.2 / 附录 G.3）：POST /smart-router/ai/gateway/chatModel
 * 请求映射 modelId→model / maxTokens→max_tokens，统一按非流式处理；
 * 响应包裹 {"code":0,"message":"success","data":{content,requestId,modelId,usage}}。
 * G.3 中 [待确认] 项按默认假设实现，需对照 ChatModelInter 实际报文校准。
 */
@RestController
public class LegacyCompatController {

    private final ChatService chatService;
    private final ObjectMapper mapper = new ObjectMapper();

    public LegacyCompatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ai/gateway/chatModel")
    public Mono<Void> chatModel(ServerWebExchange exchange, @RequestBody String body) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        PipelineContext ctx;
        try {
            ObjectNode openAiRequest = mapRequest(body);
            ctx = PipelineContextFactory.create(exchange, openAiRequest);
            ctx.setStream(false); // 旧网关不支持 SSE，统一非流式
        } catch (MasException e) {
            return writeJson(response, e.getStatus(), e.getMessage());
        }
        return chatService.execute(ctx)
                .map(this::mapResponse)
                .flatMap(json -> writeJson(response, HttpStatus.OK, json))
                .onErrorResume(e -> {
                    HttpStatus status = e instanceof MasException me
                            ? me.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
                    return writeJson(response, status, e.getMessage());
                });
    }

    /** 成功时 message 为包裹 JSON 本体；错误时生成 {"code":-1,...} */
    private Mono<Void> writeJson(ServerHttpResponse response, HttpStatus status, String payload) {
        String body;
        if (status == HttpStatus.OK) {
            body = payload;
        } else {
            response.setStatusCode(status);
            ObjectNode root = mapper.createObjectNode();
            root.put("code", -1);
            root.put("message", payload == null ? "internal error" : payload);
            root.putNull("data");
            body = root.toString();
        }
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    /** 旧协议 → OpenAI 请求映射（G.3） */
    ObjectNode mapRequest(String legacyBody) {
        JsonNode legacy;
        try {
            legacy = mapper.readTree(legacyBody);
        } catch (Exception e) {
            throw MasException.invalidParam("request body is not valid JSON");
        }
        ObjectNode req = mapper.createObjectNode();
        String modelId = legacy.path("modelId").asText("");
        if (!modelId.isBlank()) {
            req.put("model", modelId); // 缺失时走默认路由
        }
        req.set("messages", legacy.path("messages"));
        if (legacy.has("temperature")) {
            req.set("temperature", legacy.get("temperature"));
        }
        if (legacy.has("maxTokens")) {
            req.set("max_tokens", legacy.get("maxTokens"));
        }
        req.put("stream", false);
        return req;
    }

    /** OpenAI → 旧协议响应映射（G.3） */
    String mapResponse(String openAiJson) {
        try {
            JsonNode openAi = mapper.readTree(openAiJson);
            ObjectNode data = mapper.createObjectNode();
            data.put("content", openAi.path("choices").path(0).path("message").path("content").asText(""));
            data.put("requestId", openAi.path("id").asText(""));
            data.put("modelId", openAi.path("model").asText(""));
            data.set("usage", openAi.path("usage"));
            ObjectNode root = mapper.createObjectNode();
            root.put("code", 0);
            root.put("message", "success");
            root.set("data", data);
            return root.toString();
        } catch (Exception e) {
            throw MasException.internal("failed to map legacy response");
        }
    }
}

package com.sunyard.llm.mas.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.pipeline.PipelineContextFactory;
import com.sunyard.llm.mas.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 请求映射 modelId→model / maxTokens→max_tokens，支持流式；
 * 响应包裹 {"code":0,"message":"success","data":{content,requestId,modelId,usage}}。
 * §8 改进：补全参数映射 + 支持流式 + 增加错误码映射。
 */
@RestController
public class LegacyCompatController {

    private static final Logger log = LoggerFactory.getLogger(LegacyCompatController.class);

    private final ChatService chatService;
    private final ObjectMapper mapper = new ObjectMapper();

    public LegacyCompatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/ai/gateway/chatModel")
    public Mono<Void> chatModel(ServerWebExchange exchange, @RequestBody String body) {
        ServerHttpResponse response = exchange.getResponse();
        PipelineContext ctx;
        boolean isStream = false;
        try {
            ObjectNode openAiRequest = mapRequest(body);
            isStream = openAiRequest.path("stream").asBoolean(false);
            ctx = PipelineContextFactory.create(exchange, openAiRequest);
            ctx.setStream(isStream);
        } catch (MasException e) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return writeJson(response, e.getStatus(), e.getMessage());
        }

        if (isStream) {
            // §8 改进：支持流式响应
            response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
            return chatService.executeStream(ctx)
                    .map(chunk -> "data: " + chunk + "\n\n")
                    .concatWith(Mono.just("data: [DONE]\n\n"))
                    .flatMap(chunk -> writeRawChunk(response, chunk))
                    .then();
        }

        // 非流式
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return chatService.execute(ctx)
                .map(this::mapResponse)
                .flatMap(json -> writeJson(response, HttpStatus.OK, json))
                .onErrorResume(e -> {
                    HttpStatus status = e instanceof MasException me
                            ? me.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
                    return writeJson(response, status, e.getMessage());
                });
    }

    /** 流式模式：直接写入原始 chunk */
    private Mono<Void> writeRawChunk(ServerHttpResponse response, String chunk) {
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(chunk.getBytes(StandardCharsets.UTF_8))));
    }

    /** 成功时 message 为包裹 JSON 本体；错误时生成 {"code":-1,...} */
    private Mono<Void> writeJson(ServerHttpResponse response, HttpStatus status, String payload) {
        String body;
        if (status == HttpStatus.OK) {
            body = payload;
        } else {
            response.setStatusCode(status);
            int legacyCode = mapToLegacyErrorCode(status);
            ObjectNode root = mapper.createObjectNode();
            root.put("code", legacyCode);
            root.put("message", payload == null ? "internal error" : payload);
            root.putNull("data");
            body = root.toString();
        }
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    /** §8 改进：MasException 状态码映射为旧协议错误码 */
    private int mapToLegacyErrorCode(HttpStatus status) {
        return switch (status.value()) {
            case 400 -> 1000;  // 参数错误
            case 401 -> 1001;  // 鉴权失败
            case 403 -> 1002;  // 黑名单
            case 429 -> 1003;  // 限流
            case 504 -> 1004;  // 上游超时
            case 502 -> 1005;  // 上游错误
            default -> -1;
        };
    }

    /** 旧协议 → OpenAI 请求映射（G.3 + §8 改进补全参数） */
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
            req.put("model", modelId);
        }
        req.set("messages", legacy.path("messages"));
        if (legacy.has("temperature")) {
            req.set("temperature", legacy.get("temperature"));
        }
        if (legacy.has("maxTokens")) {
            req.set("max_tokens", legacy.get("maxTokens"));
        }
        // §8 改进：补全参数映射
        if (legacy.has("topP")) {
            req.set("top_p", legacy.get("topP"));
        }
        if (legacy.has("frequencyPenalty")) {
            req.set("frequency_penalty", legacy.get("frequencyPenalty"));
        }
        if (legacy.has("presencePenalty")) {
            req.set("presence_penalty", legacy.get("presencePenalty"));
        }
        if (legacy.has("stop")) {
            req.set("stop", legacy.get("stop"));
        }
        if (legacy.has("n")) {
            req.set("n", legacy.get("n"));
        }
        // §8 改进：支持流式
        boolean stream = legacy.path("stream").asBoolean(false);
        req.put("stream", stream);
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

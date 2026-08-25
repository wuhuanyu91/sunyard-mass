package com.sunyard.llm.mas.web;

import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.pipeline.PipelineContextFactory;
import com.sunyard.llm.mas.service.ChatService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * OpenAI 兼容对话接口（§4.1.1）：POST /smart-router/v1/chat/completions
 * 非流式返回 JSON；stream=true 返回 SSE（附录 G.5）。
 * 直接写响应字节流，避免编解码器对已格式化 JSON/SSE 的二次加工。
 */
@RestController
public class ChatCompletionController {

    private final ChatService chatService;

    public ChatCompletionController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/v1/chat/completions")
    public Mono<Void> chat(ServerWebExchange exchange, @RequestBody String body) {
        PipelineContext ctx = PipelineContextFactory.create(exchange, body);
        ServerHttpResponse response = exchange.getResponse();
        if (ctx.isStream()) {
            // ForwardService/回放已产出 "data: ...\n\n" 完整 SSE 帧，逐帧直写
            response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);
            return response.writeWith(chatService.executeStream(ctx)
                    .map(frame -> toBuffer(response, frame)));
        }
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(chatService.execute(ctx)
                .map(json -> toBuffer(response, json)));
    }

    private static DataBuffer toBuffer(ServerHttpResponse response, String payload) {
        return response.bufferFactory().wrap(payload.getBytes(StandardCharsets.UTF_8));
    }
}

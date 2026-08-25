package com.sunyard.llm.mas.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.service.ModelRouter;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 模型列表接口（§4.1.3）：GET /smart-router/v1/models
 * 列出启用中的对话模型（排除 embedding 等辅助模型）。
 */
@RestController
public class ModelController {

    private final ModelRouter modelRouter;
    private final ObjectMapper mapper = new ObjectMapper();

    public ModelController(ModelRouter modelRouter) {
        this.modelRouter = modelRouter;
    }

    @GetMapping("/v1/models")
    public Mono<Void> models(ServerWebExchange exchange) {
        ObjectNode root = mapper.createObjectNode();
        root.put("object", "list");
        ArrayNode data = root.putArray("data");
        modelRouter.activeModels().stream()
                .filter(c -> !"embedding".equalsIgnoreCase(c.provider()))
                .forEach(c -> {
                    ObjectNode item = data.addObject();
                    item.put("id", c.modelId());
                    item.put("object", "model");
                    item.put("owned_by", "mas");
                });
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(
                response.bufferFactory().wrap(root.toString().getBytes(StandardCharsets.UTF_8))));
    }
}

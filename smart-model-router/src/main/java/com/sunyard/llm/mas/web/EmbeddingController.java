package com.sunyard.llm.mas.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.service.ModelConfig;
import com.sunyard.llm.mas.service.ModelRouter;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Embeddings 透传接口（§4.1.2）：POST /smart-router/v1/embeddings
 * 按请求 model 查 mas_model_config 注册表，转发到对应 endpoint 的 /embeddings。
 */
@RestController
public class EmbeddingController {

    private final ModelRouter modelRouter;
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public EmbeddingController(ModelRouter modelRouter, WebClient masWebClient) {
        this.modelRouter = modelRouter;
        this.webClient = masWebClient;
    }

    @PostMapping("/v1/embeddings")
    public Mono<Void> embeddings(ServerWebExchange exchange, @RequestBody String body) {
        String model;
        try {
            JsonNode req = mapper.readTree(body);
            model = req.path("model").asText("");
        } catch (Exception e) {
            throw MasException.invalidParam("request body is not valid JSON");
        }
        ModelConfig cfg = modelRouter.getModel(model);
        if (cfg == null || !cfg.active()) {
            throw MasException.modelNotFound(model);
        }
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(webClient.post()
                .uri(cfg.endpointUrl() + "/embeddings")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .map(json -> response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8)))
                .onErrorMap(e -> !(e instanceof MasException),
                        e -> MasException.engineError(e.getMessage())));
    }
}

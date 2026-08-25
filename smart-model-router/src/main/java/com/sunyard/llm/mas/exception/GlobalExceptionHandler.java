package com.sunyard.llm.mas.exception;

import com.sunyard.llm.mas.exception.MasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一错误输出（附录 G.2）：所有异常返回 OpenAI 错误结构
 * {"error":{"message","type","code"}}。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MasException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleMasException(MasException e) {
        if (e.getStatus().is5xxServerError()) {
            log.error("MAS internal/upstream error", e);
        } else {
            log.warn("MAS rejected request: code={} message={}", e.getCode(), e.getMessage());
        }
        return Mono.just(ResponseEntity.status(e.getStatus()).body(errorBody(e)));
    }

    /** 请求体解码失败（如空 body）：按 G.2 归一为 400 invalid_param；旧协议路径按 G.3 包裹 code=-1 */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInputError(ServerWebInputException e,
                                                                      ServerWebExchange exchange) {
        log.warn("Request decode failed: {}", e.getMessage());
        MasException mapped = MasException.invalidParam("request body is missing or malformed");
        String path = exchange.getRequest().getPath().value();
        if (path.startsWith("/ai/gateway/")) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", -1);
            body.put("message", mapped.getMessage());
            body.put("data", null);
            return Mono.just(ResponseEntity.status(mapped.getStatus()).body(body));
        }
        return Mono.just(ResponseEntity.status(mapped.getStatus()).body(errorBody(mapped)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        MasException mapped = MasException.internal("Internal error");
        return Mono.just(ResponseEntity.status(mapped.getStatus()).body(errorBody(mapped)));
    }

    private Map<String, Object> errorBody(MasException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("message", e.getMessage());
        error.put("type", e.getType());
        error.put("code", e.getCode());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        return body;
    }
}

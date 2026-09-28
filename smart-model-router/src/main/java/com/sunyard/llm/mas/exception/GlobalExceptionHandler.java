package com.sunyard.llm.mas.exception;

import com.sunyard.llm.mas.exception.MasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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

    /**
     * 客户端入参校验失败：Service 层抛 IllegalArgumentException（如「app_name is required」「ruleCode 必填」）
     * 应归一为 400，而非被兜底 handler 误判为 500（此前联调测试实测：此类错误一律返回 500）。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid argument: {}", e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(MasException.invalidParam(e.getMessage()))));
    }

    /**
     * 资源/状态不存在：Service 层抛 IllegalStateException（如「app not found」「模型接入不存在」「账单不存在」）
     * 应归一为 404；此前被兜底 handler 误判为 500。
     * <p>
     * 仅"不存在"语义的 ISE 映射 404；其余（空信号哨兵、内部状态 bug 等）按 500 处理并 ERROR 留栈，
     * 避免真实服务端错误被伪装成 404 误导排障。
     */
    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleIllegalState(IllegalStateException e) {
        String msg = e.getMessage() == null ? "" : e.getMessage();
        boolean notFound = msg.contains("不存在") || msg.toLowerCase().contains("not found");
        if (notFound) {
            log.warn("Resource not found: {}", msg);
            MasException mapped = new MasException(HttpStatus.NOT_FOUND, "invalid_request_error", "not_found", msg);
            return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody(mapped)));
        }
        log.error("Illegal state (internal bug, mapped to 500)", e);
        MasException mapped = MasException.internal(msg.isEmpty() ? "Internal error" : msg);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody(mapped)));
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

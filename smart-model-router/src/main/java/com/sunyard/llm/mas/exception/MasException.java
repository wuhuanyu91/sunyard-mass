package com.sunyard.llm.mas.exception;

import org.springframework.http.HttpStatus;

/**
 * MAS 业务异常，状态码与 type/code 映射写死于附录 G.2。
 */
public class MasException extends RuntimeException {

    private final HttpStatus status;
    private final String type;
    private final String code;

    public MasException(HttpStatus status, String type, String code, String message) {
        super(message);
        this.status = status;
        this.type = type;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getType() { return type; }
    public String getCode() { return code; }

    // ---- 附录 G.2 状态码映射工厂方法 ----

    public static MasException unauthorized() {
        return new MasException(HttpStatus.UNAUTHORIZED, "authentication_error", "invalid_api_key",
                "Missing or invalid Authorization bearer token");
    }

    public static MasException blacklisted(String userId) {
        return new MasException(HttpStatus.FORBIDDEN, "permission_error", "blacklisted",
                "User is blacklisted: " + userId);
    }

    public static MasException inputBlocked(String word) {
        return new MasException(HttpStatus.BAD_REQUEST, "content_filter", "input_blocked",
                "Input contains blocked content");
    }

    public static MasException rateLimited(String userId) {
        return new MasException(HttpStatus.TOO_MANY_REQUESTS, "rate_limit_error", "rate_limited",
                "Rate limit exceeded for user: " + userId);
    }

    public static MasException invalidParam(String message) {
        return new MasException(HttpStatus.BAD_REQUEST, "invalid_request_error", "invalid_param", message);
    }

    public static MasException modelNotFound(String model) {
        return new MasException(HttpStatus.NOT_FOUND, "invalid_request_error", "model_not_found",
                "Model not registered: " + model);
    }

    public static MasException quotaExceeded() {
        return new MasException(HttpStatus.PAYMENT_REQUIRED, "quota_error", "quota_exceeded",
                "Token quota exceeded");
    }

    public static MasException engineTimeout(Throwable cause) {
        MasException e = new MasException(HttpStatus.GATEWAY_TIMEOUT, "upstream_error", "engine_timeout",
                "Upstream engine timeout");
        e.initCause(cause);
        return e;
    }

    public static MasException engineError(String message) {
        return new MasException(HttpStatus.BAD_GATEWAY, "upstream_error", "engine_error",
                "Upstream engine error: " + message);
    }

    public static MasException internal(String message) {
        return new MasException(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "internal", message);
    }
}

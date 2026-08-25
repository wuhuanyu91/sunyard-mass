package com.sunyard.llm.mas.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 附录 G.2 错误契约回归：每个工厂方法的状态码/type/code 三元组写死，不得漂移。
 */
class MasExceptionTest {

    private void assertContract(MasException e, HttpStatus status, String type, String code) {
        assertEquals(status, e.getStatus());
        assertEquals(type, e.getType());
        assertEquals(code, e.getCode());
        assertNotNull(e.getMessage());
    }

    @Test
    void allFactoryMethodsFollowG2Contract() {
        assertContract(MasException.unauthorized(), HttpStatus.UNAUTHORIZED, "authentication_error", "invalid_api_key");
        assertContract(MasException.blacklisted("u1"), HttpStatus.FORBIDDEN, "permission_error", "blacklisted");
        assertContract(MasException.inputBlocked("词"), HttpStatus.BAD_REQUEST, "content_filter", "input_blocked");
        assertContract(MasException.rateLimited("u1"), HttpStatus.TOO_MANY_REQUESTS, "rate_limit_error", "rate_limited");
        assertContract(MasException.invalidParam("bad"), HttpStatus.BAD_REQUEST, "invalid_request_error", "invalid_param");
        assertContract(MasException.modelNotFound("m1"), HttpStatus.NOT_FOUND, "invalid_request_error", "model_not_found");
        assertContract(MasException.quotaExceeded(), HttpStatus.PAYMENT_REQUIRED, "quota_error", "quota_exceeded");
        assertContract(MasException.engineTimeout(new RuntimeException()), HttpStatus.GATEWAY_TIMEOUT, "upstream_error", "engine_timeout");
        assertContract(MasException.engineError("x"), HttpStatus.BAD_GATEWAY, "upstream_error", "engine_error");
        assertContract(MasException.internal("x"), HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "internal");
    }

    @Test
    void engineTimeoutPreservesCause() {
        RuntimeException cause = new RuntimeException("connect timeout");
        MasException e = MasException.engineTimeout(cause);
        assertSame(cause, e.getCause());
    }

    @Test
    void messagesCarryContext() {
        assertEquals("Model not registered: foo-9b", MasException.modelNotFound("foo-9b").getMessage());
        assertEquals("User is blacklisted: banned-user", MasException.blacklisted("banned-user").getMessage());
    }
}

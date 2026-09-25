package com.sunyard.llm.mas.config;

import com.sunyard.llm.mas.service.AdminAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AdminAuthFilter 安全语义专项测试（银行放款前必测）：
 * <p>
 * 覆盖三类风险：
 * <ol>
 *   <li>context-path 绕过 —— 生产以 spring.webflux.base-path=/smart-router 部署，
 *       若用 request.getPath().value()（含 context-path）判断前缀，则 /internal/** 鉴权被整体绕过。
 *       此处模拟真实部署拓扑断言必须 401。</li>
 *   <li>fail-closed —— 令牌缺失/空白/无效/校验服务异常，一律不放行。</li>
 *   <li>放行边界 —— 业务端点（/v1/**）不受管理令牌约束，避免误伤正常流量。</li>
 * </ol>
 */
class AdminAuthFilterTest {

    private final AdminAuthService adminAuthService = mock(AdminAuthService.class);
    private final AdminAuthFilter filter = new AdminAuthFilter(adminAuthService);

    /** 执行过滤器，返回 chain 是否被调用（true=放行） */
    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }

    private record Outcome(int status, boolean chainInvoked) {
    }

    private Outcome run(MockServerWebExchange exchange) {
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();
        Integer code = exchange.getResponse().getStatusCode() == null
                ? null : exchange.getResponse().getStatusCode().value();
        return new Outcome(code == null ? 0 : code, chainInvoked.get());
    }

    @Test
    @DisplayName("未携带令牌访问管理端点 -> 401 且不放行")
    void missingTokenIs401() {
        Outcome o = run(exchange("/internal/rbac/users"));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), o.status());
        assertFalse(o.chainInvoked(), "无令牌不得放行");
    }

    @Test
    @DisplayName("[context-path 漏洞回归] /smart-router 前缀下管理端点必须鉴权，否则鉴权被整体绕过")
    void contextPathPrefixedAdminEndpointIs401() {
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/smart-router/internal/rbac/users")
                        .contextPath("/smart-router")
                        .build());
        // 前置校验：确保模拟的是真实部署拓扑（pathWithinApplication 已剥离 context-path）
        assertEquals("/internal/rbac/users",
                ex.getRequest().getPath().pathWithinApplication().value());
        Outcome o = run(ex);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), o.status());
        assertFalse(o.chainInvoked(), "带 context-path 的管理端点同样必须鉴权");
    }

    @Test
    @DisplayName("有效令牌 -> 放行并注入 X-Operator 供留痕")
    void validTokenPassesAndInjectsOperator() {
        when(adminAuthService.resolve("good-token")).thenReturn("admin");
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/rbac/users")
                        .header("X-Admin-Token", "good-token")
                        .build());
        Outcome o = run(ex);
        assertTrue(o.chainInvoked(), "有效令牌应放行");
        assertEquals("admin", ex.getRequest().getHeaders().getFirst("X-Operator"));
    }

    @Test
    @DisplayName("无效令牌 -> 401（服务返回空表示失败）")
    void invalidTokenIs401() {
        when(adminAuthService.resolve("bad-token")).thenReturn("");
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/rbac/users")
                        .header("X-Admin-Token", "bad-token")
                        .build());
        Outcome o = run(ex);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), o.status());
        assertFalse(o.chainInvoked());
    }

    @Test
    @DisplayName("空白令牌 -> 401（不得被当作有效值绕过）")
    void blankTokenIs401() {
        when(adminAuthService.resolve(isNull())).thenReturn(null);
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/rbac/users")
                        .header("X-Admin-Token", "   ")
                        .build());
        Outcome o = run(ex);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), o.status());
        assertFalse(o.chainInvoked());
    }

    @Test
    @DisplayName("Authorization: Bearer 形式的令牌同样被校验")
    void bearerTokenIsValidated() {
        when(adminAuthService.resolve("bearer-token")).thenReturn("ops");
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/tenants")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer bearer-token")
                        .build());
        Outcome o = run(ex);
        assertTrue(o.chainInvoked());
        assertEquals("ops", ex.getRequest().getHeaders().getFirst("X-Operator"));
    }

    @Test
    @DisplayName("令牌校验服务异常 -> fail-closed，不放行")
    void authServiceFailureIsFailClosed() {
        when(adminAuthService.resolve(anyString())).thenThrow(new IllegalStateException("db down"));
        MockServerWebExchange ex = MockServerWebExchange.from(
                MockServerHttpRequest.get("/internal/rbac/users")
                        .header("X-Admin-Token", "whatever")
                        .build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        try {
            filter.filter(ex, e -> {
                chainInvoked.set(true);
                return Mono.empty();
            }).block();
        } catch (RuntimeException ignored) {
            // 异常冒泡亦属拒绝访问
        }
        assertFalse(chainInvoked.get(), "鉴权链异常时必须 fail-closed，绝不放行");
        Integer code = ex.getResponse().getStatusCode() == null
                ? null : ex.getResponse().getStatusCode().value();
        assertTrue(code == null || code != HttpStatus.OK.value(),
                "鉴权失败不得返回 200");
    }

    @Test
    @DisplayName("业务端点（/v1/**）不受管理令牌约束，避免误伤正常业务流量")
    void businessEndpointIsNotBlocked() {
        Outcome o = run(exchange("/v1/chat/completions"));
        assertTrue(o.chainInvoked(), "业务端点不应被管理令牌过滤器拦截");
    }

    @Test
    @DisplayName("非 /internal/ 前缀的普通路径放行（放行边界明确）")
    void nonAdminPathIsNotBlocked() {
        assertTrue(run(exchange("/v1/models")).chainInvoked());
        assertTrue(run(exchange("/ai/gateway/chatModel")).chainInvoked());
        assertTrue(run(exchange("/internal-admin/whatever")).chainInvoked());
    }
}

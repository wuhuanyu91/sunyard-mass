package com.sunyard.llm.mas.config;

import com.sunyard.llm.mas.service.AdminAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 管理端点认证过滤器（此前 /internal/* 全部裸奔，银行安全团队现场 curl 即可发现）：
 * 对 /internal/** 强制校验 X-Admin-Token（或 Authorization: Bearer），无有效令牌返回 401。
 * <p>
 * 银行安全基线：鉴权永远生效，不提供任何关闭开关（原 mas.governance.admin-auth-enabled=false
 * 可整体绕过鉴权，属高危设计，已移除）。
 */
@Component
@Order(-100)
public class AdminAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);
    private static final String ADMIN_PREFIX = "/internal/";
    private static final String TOKEN_HEADER = "X-Admin-Token";

    private final AdminAuthService adminAuthService;

    public AdminAuthFilter(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 用 pathWithinApplication() 剥离 context-path（如 /smart-router），
        // 否则含 context-path 的完整路径永远不以 "/internal/" 开头，导致鉴权被完全绕过
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith(ADMIN_PREFIX)) {
            return chain.filter(exchange);
        }
        String token = exchange.getRequest().getHeaders().getFirst(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            String bearer = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (bearer != null && bearer.startsWith("Bearer ")) {
                token = bearer.substring(7);
            }
        }
        String user = adminAuthService.resolve(token);
        if (user == null || user.isEmpty()) {
            log.warn("admin endpoint rejected (401): path={}, remote={}", path,
                    exchange.getRequest().getRemoteAddress());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"error\":{\"code\":\"unauthorized\",\"message\":\"管理端点需提供有效的 X-Admin-Token\"}}";
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
        // 透传操作人，供各 Controller 的 X-Operator 留痕使用
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header("X-Operator", user))
                .build();
        return chain.filter(mutated);
    }
}

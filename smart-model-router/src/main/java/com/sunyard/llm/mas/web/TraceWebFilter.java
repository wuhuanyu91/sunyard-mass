package com.sunyard.llm.mas.web;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * trace_id 生成（附录 G.8）：入口生成 UUID，写入响应头 X-Trace-Id 与请求属性，贯穿全链路。
 */
@Component
@Order(-10)
public class TraceWebFilter implements WebFilter {

    public static final String TRACE_ID_ATTR = "mas.traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        exchange.getAttributes().put(TRACE_ID_ATTR, traceId);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        return chain.filter(exchange);
    }
}

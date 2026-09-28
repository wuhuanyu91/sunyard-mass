package com.sunyard.llm.mas.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sunyard.llm.mas.service.AdminAuthService;
import com.sunyard.llm.mas.service.RbacService;
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

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 管理端点认证 + RBAC 权限 enforcement 过滤器：
 * <ol>
 *   <li>认证：对 /internal/** 强制校验 X-Admin-Token（或 Authorization: Bearer），无有效令牌 401；</li>
 *   <li>鉴权：按 RBAC 权限矩阵判定用户对目标模块的权限级别（DENY/READ/WRITE/ADMIN），不足 403。
 *       此前只有令牌校验、任何有效令牌可访问全部 /internal/*（有矩阵无执行），属公告二-4 缺口。</li>
 * </ol>
 * 映射规则：路由前缀 → 模块；方法 → 级别（GET=READ / 写=WRITE / DELETE=ADMIN）；
 * RBAC 管理与令牌管理端点一律 ADMIN；system 模块写操作一律 ADMIN。
 * fail-closed：未知前缀按 system+ADMIN 处理；权限判定异常按无权限处理（带 30s 缓存）。
 * <p>
 * 银行安全基线：鉴权永远生效，不提供任何关闭开关。
 */
@Component
@Order(-100)
public class AdminAuthFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);
    private static final String ADMIN_PREFIX = "/internal/";
    private static final String TOKEN_HEADER = "X-Admin-Token";

    /** 免令牌端点：登录（令牌签发入口本身）；采集 ingest 走独立的 X-Push-Token 源级鉴权 */
    private static final Set<String> WHITELIST_EXACT = Set.of(
            "/internal/auth/login",
            "/internal/collection/ingest");

    /** 路由前缀 → RBAC 模块（与 mas_sys_role_permission.module 口径一致） */
    private static final Map<String, String> MODULE_BY_PREFIX = Map.ofEntries(
            Map.entry("/internal/dashboard", "dashboard"),
            Map.entry("/internal/metering", "metering"),
            Map.entry("/internal/billing", "metering"),
            Map.entry("/internal/pricing", "metering"),
            Map.entry("/internal/routing", "routing"),
            Map.entry("/internal/models", "modelAsset"),
            Map.entry("/internal/security", "security"),
            Map.entry("/internal/api-keys", "apiKey"),
            Map.entry("/internal/cache", "cache"),
            Map.entry("/internal/apps", "apps"),
            Map.entry("/internal/tenants", "tenant"),
            Map.entry("/internal/policies", "policy"),
            Map.entry("/internal/compute", "compute"),
            Map.entry("/internal/collection", "system"),
            Map.entry("/internal/integration", "system"),
            Map.entry("/internal/system", "system"),
            Map.entry("/internal/oplog", "system"),
            Map.entry("/internal/rbac", "system"),
            Map.entry("/internal/admin-auth", "system"),
            Map.entry("/internal/auth", "system"));

    /** 无论方法，命中即要求 ADMIN 的高敏前缀 */
    private static final Set<String> ALWAYS_ADMIN_PREFIX = Set.of(
            "/internal/rbac", "/internal/admin-auth", "/internal/oplog");

    private final AdminAuthService adminAuthService;
    private final RbacService rbacService;

    /** 权限判定缓存：角色/授权变更后最长 30s 生效（避免每请求查库） */
    private final Cache<String, Boolean> permCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30)).maximumSize(5000).build();

    public AdminAuthFilter(AdminAuthService adminAuthService, RbacService rbacService) {
        this.adminAuthService = adminAuthService;
        this.rbacService = rbacService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 用 pathWithinApplication() 剥离 context-path（如 /smart-router），
        // 否则含 context-path 的完整路径永远不以 "/internal/" 开头，导致鉴权被完全绕过
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith(ADMIN_PREFIX)) {
            return chain.filter(exchange);
        }
        // CORS 预检与白名单端点直接放行
        if ("OPTIONS".equals(exchange.getRequest().getMethod().name()) || WHITELIST_EXACT.contains(path)) {
            return chain.filter(exchange);
        }
        String token = exchange.getRequest().getHeaders().getFirst(TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            String bearer = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (bearer != null && bearer.startsWith("Bearer ")) {
                token = bearer.substring(7);
            }
        }
        final String user = adminAuthService.resolve(token);
        if (user == null || user.isEmpty()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED,
                    "管理端点需提供有效的 X-Admin-Token", path, 401);
        }
        // 透传操作人，供各 Controller 的 X-Operator 留痕使用
        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header("X-Operator", user))
                .build();

        String module = resolveModule(path);
        String required = resolveRequiredLevel(path, exchange.getRequest().getMethod().name());
        String cacheKey = user + '|' + module + '|' + required;
        Boolean cached = permCache.getIfPresent(cacheKey);
        Mono<Boolean> decision = cached != null
                ? Mono.just(cached)
                : rbacService.hasPermission(user, module, required)
                        .doOnNext(ok -> permCache.put(cacheKey, ok));

        return decision.flatMap(ok -> {
            if (Boolean.TRUE.equals(ok)) {
                return chain.filter(mutated);
            }
            log.warn("admin endpoint forbidden (403): path={}, user={}, need={} on module={}",
                    path, user, required, module);
            return reject(exchange, HttpStatus.FORBIDDEN,
                    "当前用户无 " + module + " 模块 " + required + " 权限", path, 403);
            // 权限判定链路异常（DB 不可用等）：fail-closed，按无权限处理
        }).onErrorResume(e -> {
            log.error("permission check failed (fail-closed): path={}, user={}", path, user, e);
            return reject(exchange, HttpStatus.FORBIDDEN, "权限判定失败，已拒绝访问", path, 403);
        });
    }

    /** 路由前缀 → 模块；未知前缀一律归入 system（fail-closed：默认最高要求） */
    private String resolveModule(String path) {
        for (Map.Entry<String, String> e : MODULE_BY_PREFIX.entrySet()) {
            if (path.startsWith(e.getKey())) return e.getValue();
        }
        return "system";
    }

    /** 方法 → 权限级别：GET=READ / DELETE=ADMIN / 其他写=WRITE；高敏前缀与 system 写操作一律 ADMIN */
    private String resolveRequiredLevel(String path, String method) {
        for (String p : ALWAYS_ADMIN_PREFIX) {
            if (path.startsWith(p)) return "ADMIN";
        }
        boolean isGet = "GET".equals(method) || "HEAD".equals(method);
        if (isGet) return "READ";
        if ("DELETE".equals(method)) return "ADMIN";
        if (path.startsWith("/internal/system") || path.startsWith("/internal/auth")) return "ADMIN";
        return "WRITE";
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message, String path, int codeNo) {
        if (codeNo == 401) {
            log.warn("admin endpoint rejected (401): path={}, remote={}", path,
                    exchange.getRequest().getRemoteAddress());
        }
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":{\"code\":\"" + (codeNo == 401 ? "unauthorized" : "forbidden")
                + "\",\"message\":\"" + message + "\"}}";
        return exchange.getResponse().writeWith(
                Mono.just(exchange.getResponse().bufferFactory().wrap(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}

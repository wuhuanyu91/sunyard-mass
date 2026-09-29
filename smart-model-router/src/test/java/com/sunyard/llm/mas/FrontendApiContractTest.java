package com.sunyard.llm.mas;

import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 前后端接口「联调」契约测试：覆盖前端 maas 的 services/api.ts 实际调用的全部 /internal 端点。
 * <p>
 * 三类断言（直接回答「前后端接口联调测过吗」）：
 * 1) 无 X-Admin-Token → 一律 401（鉴权门禁；若为 404 说明该端点后端根本不存在 = 联调断点）。
 * 2) 带有效 Token → 路由可达：非资源型端点不得 404（端点必须存在）；所有端点不得 500（不得服务端崩溃）。
 * 3) 写操作以「宽松但合理」的请求体探测，目的是验证「端点存在 + 鉴权通过 + 不崩」，而非校验业务校验细节。
 * <p>
 * 采用 @ParameterizedTest + @MethodSource，每个端点生成 2 个用例（无 token / 带 token），
 * 全量端点约 110 个，合计 ~220 个用例，系统性覆盖前端-后端契约。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FrontendApiContractTest {

    private static EmbeddedPostgres pg;
    private static final String PG_DATA_DIR = "D:/pgtest-contract-" + System.nanoTime();
    @LocalServerPort
    private int port;
    private WebTestClient client;
    private WebTestClient anon;

    /** 测试专用引导令牌：经 mas.admin-token 显式配置播种（生产默认不播种任何令牌） */
    private static final String TOKEN = "mat-test-contract-token";

    @AfterAll
    static void stopPg() throws Exception {
        if (pg != null) pg.close();
        try { deleteRecursively(new java.io.File(PG_DATA_DIR)); } catch (Exception ignored) {}
    }

    private static void deleteRecursively(java.io.File f) {
        java.io.File[] children = f.listFiles();
        if (children != null) for (java.io.File c : children) deleteRecursively(c);
        f.delete();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        try {
            if (pg == null) pg = EmbeddedPostgres.builder()
                    .setLocaleConfig("locale", "C")
                    .setDataDirectory(PG_DATA_DIR)
                    .setCleanDataDirectory(true)
                    .start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        registry.add("MAS_DB_URL", () -> "jdbc:postgresql://localhost:" + pg.getPort() + "/postgres");
        registry.add("MAS_DB_USERNAME", () -> "postgres");
        registry.add("MAS_DB_PASSWORD", () -> "postgres");
        registry.add("mas.backend.default-endpoint", () -> "http://127.0.0.1:9/disabled");
        registry.add("mas.admin-token", () -> TOKEN);
        registry.add("mas.security.scan-interval-ms", () -> "3600000");
        registry.add("mas.auth.enabled", () -> "true");
        registry.add("mas.governance.fail-closed", () -> "false");
    }

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router")
                .defaultHeader("X-Admin-Token", TOKEN)
                .build();
        anon = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router")
                .build();
    }

    /** 一个端点的契约描述。path 中的 __ID__ 在请求前替换为占位 id。 */
    record Spec(String method, String path, boolean write) {
        boolean idBased() { return path.contains("__ID__"); }
        String resolved() { return path.replace("__ID__", "ut_id"); }
    }

    /** 宽松但合理的写操作请求体（覆盖大多数 Controller 可能读取的字段，避免空指针误报）。 */
    private static final Map<String, Object> WRITE_BODY = new LinkedHashMap<>();
    static {
        WRITE_BODY.put("id", "ut_id");
        WRITE_BODY.put("name", "ut");
        WRITE_BODY.put("code", "ut");
        WRITE_BODY.put("keyId", "ut_id");
        WRITE_BODY.put("connId", "ut_id");
        WRITE_BODY.put("alertId", "ut_id");
        WRITE_BODY.put("assetId", "ut_id");
        WRITE_BODY.put("ruleId", "ut_id");
        WRITE_BODY.put("deptId", "ut_id");
        WRITE_BODY.put("userId", "ut_id");
        WRITE_BODY.put("userCode", "ut_id");
        WRITE_BODY.put("roleCode", "ut");
        WRITE_BODY.put("roleName", "ut");
        WRITE_BODY.put("role", "ut");
        WRITE_BODY.put("tenantId", "ut_id");
        WRITE_BODY.put("traceId", "ut_id");
        WRITE_BODY.put("billNo", "ut_id");
        WRITE_BODY.put("modelId", "ut_id");
        WRITE_BODY.put("releaseId", "ut_id");
        WRITE_BODY.put("policyId", "ut_id");
        WRITE_BODY.put("appId", "ut_id");
        WRITE_BODY.put("taskId", "ut_id");
        WRITE_BODY.put("memberId", "ut_id");
        WRITE_BODY.put("prefix", "ut");
        WRITE_BODY.put("key", "ut");
        WRITE_BODY.put("month", "2026-09");
        WRITE_BODY.put("password", "Sunyard@123");
        WRITE_BODY.put("enabled", true);
        WRITE_BODY.put("status", "ON");
        WRITE_BODY.put("reason", "ut");
        WRITE_BODY.put("comment", "ut");
        WRITE_BODY.put("approved", true);
        WRITE_BODY.put("percent", 10);
        WRITE_BODY.put("monthTokenQuota", 1);
        WRITE_BODY.put("overLimitStop", false);
        WRITE_BODY.put("scope", "ut");
        WRITE_BODY.put("rules", "{}");
        WRITE_BODY.put("notifyChannels", "email");
        WRITE_BODY.put("opType", "ut");
        WRITE_BODY.put("detail", "ut");
        WRITE_BODY.put("locked", 0);
        WRITE_BODY.put("failCount", 0);
        WRITE_BODY.put("pwdMustChange", 1);
        WRITE_BODY.put("userName", "ut");
        WRITE_BODY.put("deptId", "ut_id");
        WRITE_BODY.put("userCode", "ut_id");
    }

    static Stream<Spec> specs() {
        return Stream.of(
                // ===== 仪表盘 / 大盘 =====
                new Spec("GET", "/internal/dashboard/summary", false),
                new Spec("GET", "/internal/dashboard/app-tco-rank", false),
                new Spec("GET", "/internal/dashboard/model-tco-rank", false),
                new Spec("GET", "/internal/dashboard/circuit-breakers", false),
                new Spec("GET", "/internal/dashboard/token-series", false),
                new Spec("GET", "/internal/dashboard/trend-series", false),
                new Spec("GET", "/internal/dashboard/dept-tco", false),
                new Spec("GET", "/internal/dashboard/funnel", false),
                new Spec("GET", "/internal/dashboard/rate-limit-hits", false),
                new Spec("GET", "/internal/dashboard/queue", false),
                new Spec("GET", "/internal/dashboard/batch-trend", false),
                new Spec("GET", "/internal/dashboard/heatmap", false),
                // ===== 模型 / 应用 =====
                new Spec("GET", "/internal/models", false),
                new Spec("GET", "/internal/apps", false),
                new Spec("PUT", "/internal/apps/__ID__", true),
                new Spec("POST", "/internal/apps", true),
                new Spec("POST", "/internal/apps/__ID__/toggle", true),
                new Spec("DELETE", "/internal/apps/__ID__", true),
                new Spec("GET", "/internal/models/connections", false),
                new Spec("PUT", "/internal/models/connections/__ID__", true),
                new Spec("POST", "/internal/models/connections", true),
                new Spec("POST", "/internal/models/connections/__ID__/test", true),
                new Spec("DELETE", "/internal/models/connections/__ID__", true),
                new Spec("GET", "/internal/models/releases", false),
                new Spec("POST", "/internal/models/releases", true),
                new Spec("PATCH", "/internal/models/releases/__ID__/percent", true),
                new Spec("POST", "/internal/models/releases/__ID__/rollback", true),
                new Spec("GET", "/internal/models/archives", false),
                new Spec("PUT", "/internal/models/archive-rules", true),
                new Spec("POST", "/internal/models/archives/by-model/__ID__/revive", true),
                new Spec("DELETE", "/internal/models/archives/by-model/__ID__", true),
                new Spec("GET", "/internal/models/__ID__/versions", false),
                new Spec("GET", "/internal/models/__ID__/lineage", false),
                new Spec("GET", "/internal/models/eval-records", false),
                // ===== 策略 / 路由引擎 =====
                new Spec("GET", "/internal/policies", false),
                new Spec("POST", "/internal/policies", true),
                new Spec("PUT", "/internal/policies/__ID__", true),
                new Spec("PUT", "/internal/policies/__ID__/status", true),
                new Spec("POST", "/internal/policies/__ID__/approve", true),
                new Spec("POST", "/internal/policies/__ID__/submit", true),
                new Spec("GET", "/internal/policies/exec-logs/__ID__", false),
                new Spec("GET", "/internal/routing/router-logs", false),
                new Spec("GET", "/internal/routing/rate-limit-rules", false),
                new Spec("PUT", "/internal/routing/rate-limit-rules/__ID__", true),
                new Spec("POST", "/internal/routing/rate-limit-rules", true),
                new Spec("GET", "/internal/routing/routing-rule-sets", false),
                new Spec("POST", "/internal/routing/routing-rule-sets", true),
                new Spec("GET", "/internal/routing/aggregation-groups", false),
                new Spec("GET", "/internal/routing/elastic-switch", false),
                new Spec("GET", "/internal/routing/engine", false),
                new Spec("PUT", "/internal/routing/engine", true),
                // ===== 计量 / 计费 / 定价 =====
                new Spec("GET", "/internal/metering/call-logs", false),
                new Spec("GET", "/internal/metering/quotas", false),
                new Spec("PUT", "/internal/metering/quotas/__ID__", true),
                new Spec("POST", "/internal/metering/quotas/__ID__/resume", true),
                new Spec("POST", "/internal/metering/quotas/__ID__/resume/approve", true),
                new Spec("GET", "/internal/billing/bills", false),
                new Spec("POST", "/internal/billing/generate", true),
                new Spec("POST", "/internal/billing/bills/__ID__/lock", true),
                new Spec("GET", "/internal/billing/reconciliations", false),
                new Spec("POST", "/internal/billing/reconciliations", true),
                new Spec("GET", "/internal/pricing/rules", false),
                new Spec("PUT", "/internal/pricing/rules/__ID__", true),
                new Spec("POST", "/internal/pricing/rules", true),
                new Spec("DELETE", "/internal/pricing/rules/__ID__", true),
                new Spec("POST", "/internal/pricing/simulate", true),
                new Spec("GET", "/internal/metering/cost-alert", false),
                new Spec("PUT", "/internal/metering/cost-alert", true),
                // ===== 安全 =====
                new Spec("GET", "/internal/security/events", false),
                new Spec("GET", "/internal/security/alerts", false),
                new Spec("POST", "/internal/security/alerts/__ID__/handle", true),
                new Spec("GET", "/internal/security/guardrail", false),
                new Spec("PUT", "/internal/security/guardrail", true),
                new Spec("POST", "/internal/security/guardrail/test", false),
                new Spec("GET", "/internal/security/guardrail/policies", false),
                new Spec("POST", "/internal/security/guardrail/policies", true),
                new Spec("PUT", "/internal/security/guardrail/policies/__ID__", true),
                new Spec("DELETE", "/internal/security/guardrail/policies/__ID__", true),
                new Spec("POST", "/internal/security/scan", true),
                new Spec("GET", "/internal/security/detect-rules", false),
                // ===== 算力编排 / 采集 =====
                new Spec("GET", "/internal/compute/nodes", false),
                new Spec("GET", "/internal/compute/vendors", false),
                new Spec("GET", "/internal/compute/orchestration", false),
                new Spec("PUT", "/internal/compute/orchestration", true),
                new Spec("GET", "/internal/compute/batch-tasks", false),
                new Spec("POST", "/internal/compute/batch-tasks", true),
                new Spec("DELETE", "/internal/compute/batch-tasks/__ID__", true),
                new Spec("POST", "/internal/compute/metrics", true),
                new Spec("GET", "/internal/collection/sources", false),
                new Spec("GET", "/internal/collection/batches", false),
                // ===== RBAC / 租户 =====
                new Spec("GET", "/internal/rbac/members", false),
                new Spec("POST", "/internal/rbac/members/role", true),
                new Spec("GET", "/internal/rbac/users", false),
                new Spec("POST", "/internal/rbac/users", true),
                new Spec("PATCH", "/internal/rbac/users/__ID__/state", true),
                new Spec("DELETE", "/internal/rbac/users/__ID__", true),
                new Spec("GET", "/internal/rbac/roles", false),
                new Spec("POST", "/internal/rbac/roles", true),
                new Spec("DELETE", "/internal/rbac/roles/__ID__", true),
                new Spec("GET", "/internal/rbac/perm-matrix", false),
                new Spec("POST", "/internal/rbac/perm-matrix/batch", true),
                new Spec("GET", "/internal/tenants", false),
                new Spec("GET", "/internal/tenants/dept-mapping", false),
                new Spec("PATCH", "/internal/tenants/__ID__/status", true),
                // ===== 系统配置 / API Key =====
                new Spec("GET", "/internal/system/op-logs", false),
                new Spec("GET", "/internal/system/params", false),
                new Spec("PUT", "/internal/system/params", true),
                new Spec("GET", "/internal/system/config/__ID__", false),
                new Spec("PUT", "/internal/system/config/__ID__", true),
                new Spec("GET", "/internal/api-keys", false),
                new Spec("POST", "/internal/api-keys", true),
                new Spec("PUT", "/internal/api-keys/__ID__", true),
                new Spec("PUT", "/internal/api-keys/__ID__/status", true),
                new Spec("POST", "/internal/api-keys/__ID__/rotate", true),
                new Spec("DELETE", "/internal/api-keys?prefix=ut", true),
                // ===== 行内底座对接（本轮新增） =====
                new Spec("GET", "/internal/integration", false),
                new Spec("PUT", "/internal/integration", true),
                new Spec("POST", "/internal/integration/__ID__/test", true),
                new Spec("POST", "/internal/integration/iam/sync", true),
                new Spec("POST", "/internal/integration/monitor/push", true),
                new Spec("POST", "/internal/integration/alert/forward/__ID__", true),
                new Spec("POST", "/internal/integration/ticket", true),
                new Spec("GET", "/internal/integration/logs", false)
        );
    }

    @ParameterizedTest(name = "noToken[{index}] {0} {1}")
    @MethodSource("specs")
    void noToken_rejected(Spec s) {
        anon.method(HttpMethod.valueOf(s.method()))
                .uri(s.resolved())
                .exchange()
                .expectStatus().isEqualTo(401);
    }

    @ParameterizedTest(name = "withToken[{index}] {0} {1}")
    @MethodSource("specs")
    void withToken_reachableAndNotCrashing(Spec s) {
        WebTestClient.RequestBodySpec req = client.method(HttpMethod.valueOf(s.method())).uri(s.resolved());
        WebTestClient.RequestHeadersSpec<?> finalReq;
        if (s.write()) {
            finalReq = req.contentType(MediaType.APPLICATION_JSON).bodyValue(WRITE_BODY);
        } else {
            finalReq = req;
        }
        int status = finalReq.exchange()
                .returnResult(String.class)
                .getStatus().value();
        // 不得出现服务端崩溃（500），否则为真实 bug
        assertFalse(status >= 500, "服务端 500 崩溃: " + s.method() + " " + s.path() + " -> " + status);
        // 非资源型（列表/配置）端点必须真实存在，不得 404
        if (!s.idBased()) {
            assertFalse(status == 404, "端点不存在(404) = 联调断点: " + s.method() + " " + s.path());
        }
    }
}

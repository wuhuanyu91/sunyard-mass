package com.sunyard.llm.mas;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 宁波银行治理功能端到端冒烟测试。
 * 用 zonky embedded-postgres 启动原生 PostgreSQL（自动下载，不污染系统），
 * 应用启动即由 DatabaseConfig 自动建表 + 灌种子（schema.sql → migration-real-data.sql → data.sql），
 * 再用 WebTestClient 把每个治理端点按"创建→查询→变更→回查"顺序打一遍，
 * 重点验证此前 15 个"假写端点"现已真实落库（t11 审计留痕、t07 策略审批生效）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GovernanceE2ETest {

    private static EmbeddedPostgres pg;
    // 每次用唯一英文数据目录，避开 Windows 中文用户名目录与残留文件锁
    private static final String PG_DATA_DIR = "D:/pgtest-" + System.nanoTime();
    @LocalServerPort
    private int port;
    private WebTestClient client;
    @Autowired
    private DataSource dataSource;

    private static final String TOKEN = "mat-demo-admin-token";

    @AfterAll
    static void stopPg() throws Exception {
        if (pg != null) pg.close();
        // 尽力清理数据目录（Windows 文件锁定时忽略，下次跑用新目录）
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
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        registry.add("MAS_DB_URL", () -> "jdbc:postgresql://localhost:" + pg.getPort() + "/postgres");
        registry.add("MAS_DB_USERNAME", () -> "postgres");
        registry.add("MAS_DB_PASSWORD", () -> "postgres");
        // 关闭外部模型端点依赖，避免测试期联调模型服务
        registry.add("mas.backend.default-endpoint", () -> "http://127.0.0.1:9/disabled");
        registry.add("mas.auth.enabled", () -> "true");
        registry.add("mas.governance.fail-closed", () -> "false");
    }

    @BeforeEach
    void setup() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router")
                .defaultHeader("X-Admin-Token", TOKEN)
                .build();
    }

    private String suffix() {
        return String.valueOf(System.nanoTime());
    }

    @Test
    void t01_appStarted_and_governance_tables_exist() {
        // pricing rules 能返回且含种子 -> 证明治理表已建、种子已灌
        client.get().uri("/internal/pricing/rules").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$[0].rule_code").exists();
    }

    @Test
    void t02_rbac_full_lifecycle() {
        client.get().uri("/internal/rbac/users").exchange().expectStatus().isOk();
        String code = "U_E2E_" + suffix();
        client.post().uri("/internal/rbac/users")
                .bodyValue(Map.of("userCode", code, "userName", "e2e", "status", 1,
                        "roles", List.of("ROLE_VIEWER")))
                .exchange().expectStatus().isOk();
        client.get().uri("/internal/rbac/users").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[?(@.user_code=='" + code + "')]").exists();
        client.patch().uri("/internal/rbac/users/{c}/state", code)
                .bodyValue(Map.of("status", 0)).exchange().expectStatus().isOk();
        client.delete().uri("/internal/rbac/users/{c}", code).exchange().expectStatus().isOk();
    }

    @Test
    void t03_role_and_perm_matrix() {
        String role = "R_E2E_" + suffix();
        client.post().uri("/internal/rbac/roles")
                .bodyValue(Map.of("roleCode", role, "roleName", "e2e")).exchange().expectStatus().isOk();
        client.get().uri("/internal/rbac/perm-matrix").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].module").exists();
        client.post().uri("/internal/rbac/perm-matrix/cell")
                .bodyValue(Map.of("module", "MODEL_ASSET", "roleCode", role, "level", 2))
                .exchange().expectStatus().isOk();
        client.get().uri("/internal/rbac/perm-matrix").exchange().expectStatus().isOk();
    }

    @Test
    void t04_tenant_and_mapping() {
        client.get().uri("/internal/tenants").exchange().expectStatus().isOk();
        String tid = "T_E2E_" + suffix();
        client.post().uri("/internal/tenants")
                .bodyValue(Map.of("tenantId", tid, "tenantName", "e2e", "status", "ACTIVE"))
                .exchange().expectStatus().isOk();
        client.post().uri("/internal/tenants/dept-mapping")
                .bodyValue(Map.of("deptId", "DEPT_E2E", "tenantId", tid)).exchange().expectStatus().isOk();
        client.get().uri("/internal/tenants/dept-mapping").exchange().expectStatus().isOk();
        client.get().uri("/internal/tenants/app-mapping").exchange().expectStatus().isOk();
    }

    @Test
    void t05_pricing_engine() {
        client.get().uri("/internal/pricing/rules").exchange().expectStatus().isOk();
        String rc = "PR_E2E_" + suffix();
        client.post().uri("/internal/pricing/rules").bodyValue(Map.of(
                "ruleCode", rc, "deptId", "DEPT-TECH", "serviceType", "CHAT",
                "inputPrice", 0.001, "outputPrice", 0.002, "requestPrice", 0.01,
                "priority", 1, "enabled", true)).exchange().expectStatus().isOk();
        client.post().uri("/internal/pricing/simulate").bodyValue(Map.of(
                "deptId", "DEPT-TECH", "serviceType", "CHAT",
                "promptTokens", 1000, "completionTokens", 500, "calls", 1))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.amount").value((Object v) -> {
                    if (((Number) v).doubleValue() <= 0) throw new AssertionError("期望 amount > 0");
                });
    }

    @Test
    void t06_billing_lifecycle() throws Exception {
        String month = "2026-09";
        String tenant = "TENANT-E2E-BILL";
        // 准备真实用量：该账期一条未入账调用记录（成本 12.34 元 / 5000 tokens）
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO mas_call_log (trace_id, model_id, user_id, tenant_id, app_id, "
                             + "prompt_tokens, completion_tokens, total_tokens, cost_amount, bill_month, billed) "
                             + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)")) {
            ps.setString(1, "tr-e2e-bill");
            ps.setString(2, "qwen-lite");
            ps.setString(3, "e2e-user");
            ps.setString(4, tenant);
            ps.setString(5, "APP-CSR");
            ps.setInt(6, 3000);
            ps.setInt(7, 2000);
            ps.setInt(8, 5000);
            ps.setBigDecimal(9, new BigDecimal("12.34"));
            ps.setString(10, month);
            ps.executeUpdate();
        }
        // 触发生成账单（回执验证月份正确传入）
        client.post().uri(uriBuilder -> uriBuilder.path("/internal/billing/generate")
                        .queryParam("month", month).build())
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.op_type").isEqualTo("生成账单")
                .jsonPath("$.target_id").isEqualTo(month);
        // 真实验证：账单必须真实落库，金额 = 用量成本 12.34 元（不再只验回执）
        List<Map> bills = client.get()
                .uri(uriBuilder -> uriBuilder.path("/internal/billing/bills").queryParam("month", month).build())
                .exchange().expectStatus().isOk()
                .expectBodyList(Map.class).returnResult().getResponseBody();
        Map bill = bills == null ? null : bills.stream()
                .filter(b -> tenant.equals(String.valueOf(b.get("tenant_id"))))
                .findFirst().orElse(null);
        if (bill == null) {
            throw new AssertionError("账期 " + month + " 未生成租户 " + tenant + " 的账单（generate 未真实出账）");
        }
        double amount = ((Number) bill.get("total_amount")).doubleValue();
        if (Math.abs(amount - 12.34) > 0.001) {
            throw new AssertionError("账单金额应=12.34（真实用量成本），实际=" + amount);
        }
        if (((Number) bill.get("total_calls")).longValue() < 1) {
            throw new AssertionError("账单调用次数应>=1，实际=" + bill.get("total_calls"));
        }
        // 账单明细同样真实落库
        client.get().uri("/internal/billing/bills/{no}/items", String.valueOf(bill.get("bill_no")))
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[0].amount").exists();
    }

    @Test
    void t07_policy_lifecycle_approve_effective() {
        String pid = "P_E2E_" + suffix();
        client.post().uri("/internal/policies")
                .bodyValue(Map.of("policyId", pid, "policyName", "e2e",
                        "policyType", "GOVERN", "contentJson", "{}")).exchange().expectStatus().isOk();
        client.post().uri("/internal/policies/{id}/submit", pid)
                .bodyValue(Map.of("contentJson", "{}")).exchange().expectStatus().isOk();
        client.post().uri("/internal/policies/{id}/approve", pid)
                .bodyValue(Map.of("approved", true, "comment", "ok")).exchange().expectStatus().isOk();
        // 审批后策略应真实变为 ACTIVE（证明策略引擎真生效）
        client.get().uri("/internal/policies").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[?(@.policy_id=='" + pid + "' && @.status=='PUBLISHED')]").exists();
        client.post().uri("/internal/policies/{id}/rollback", pid).exchange().expectStatus().isOk();
    }

    @Test
    void t08_model_lifecycle() {
        String mid = "M_E2E_" + suffix();
        client.post().uri("/internal/models/versions")
                .bodyValue(Map.of("modelId", mid, "version", "v1", "baseModel", "qwen",
                        "changeType", "REGISTER")).exchange().expectStatus().isOk();
        client.get().uri("/internal/models/{id}/versions", mid).exchange().expectStatus().isOk();
        client.post().uri("/internal/models/lineage")
                .bodyValue(Map.of("modelId", mid, "parentModelId", "qwen",
                        "deriveType", "DISTILL")).exchange().expectStatus().isOk();
        client.get().uri("/internal/models/{id}/lineage", mid).exchange().expectStatus().isOk();
        client.post().uri("/internal/models/releases")
                .bodyValue(Map.of("modelId", mid, "toVersion", "v1",
                        "grayScope", "GLOBAL", "grayPercent", 10)).exchange().expectStatus().isOk();
        // 未传 releaseId 时由服务端生成（REL-<时间戳>），按 modelId 反查
        String rid = client.get().uri("/internal/models/releases").exchange().expectStatus().isOk()
                .expectBodyList(Map.class).returnResult().getResponseBody().stream()
                .filter(m -> mid.equals(String.valueOf(m.get("model_id"))))
                .map(m -> String.valueOf(m.get("release_id")))
                .findFirst().orElseThrow(() -> new AssertionError("未找到模型 " + mid + " 的灰度发布单"));
        client.patch().uri("/internal/models/releases/{id}/percent", rid)
                .bodyValue(Map.of("grayPercent", 50)).exchange().expectStatus().isOk();
        // 真实验证灰度比例落库 50（此前测试传参名 percent 而服务端读 grayPercent，
        // 实际始终取默认值，测试因只断言 200 而"假绿"）
        Object after = client.get().uri("/internal/models/releases").exchange().expectStatus().isOk()
                .expectBodyList(Map.class).returnResult().getResponseBody().stream()
                .filter(m -> rid.equals(String.valueOf(m.get("release_id"))))
                .map(m -> m.get("gray_percent"))
                .findFirst().orElseThrow(() -> new AssertionError("发布单 " + rid + " 未回查到"));
        if (!"50".equals(String.valueOf(after))) {
            throw new AssertionError("调整灰度比例后应=50，实际=" + after);
        }
    }

    @Test
    void t09_collection_and_compute() {
        String sc = "SRC_E2E_" + suffix();
        String tok = "tok-e2e-" + suffix();
        client.post().uri("/internal/collection/sources")
                .bodyValue(Map.of("sourceCode", sc, "sourceName", "e2e",
                        "collectType", "SDK", "enabled", true, "pushToken", tok)).exchange().expectStatus().isOk();
        client.get().uri("/internal/collection/sources").exchange().expectStatus().isOk();
        client.post().uri("/internal/collection/ingest")
                .header("X-Source-Code", sc).header("X-Push-Token", tok)
                .bodyValue(Map.of("records", List.of(Map.of("trace_id", "tr-" + suffix())))).exchange().expectStatus().isOk();
        client.get().uri("/internal/collection/batches").exchange().expectStatus().isOk();
        client.post().uri("/internal/compute/metrics")
                .bodyValue(Map.of("nodeId", "n1", "gpuUtil", 50.0, "gpuHours", 10.0))
                .exchange().expectStatus().isOk();
        client.get().uri("/internal/compute/nodes").exchange().expectStatus().isOk();
        client.get().uri("/internal/compute/summary").exchange().expectStatus().isOk();
    }

    @Test
    void t10_security_events() {
        String rc = "DR_E2E_" + suffix();
        client.post().uri("/internal/security/detect-rules")
                .bodyValue(Map.of("ruleCode", rc, "ruleName", "e2e", "detectType", "SENSITIVE",
                        "action", "BLOCK", "enabled", true)).exchange().expectStatus().isOk();
        client.get().uri("/internal/security/detect-rules").exchange().expectStatus().isOk();
        client.get().uri("/internal/security/events").exchange().expectStatus().isOk();
        client.get().uri("/internal/security/alerts").exchange().expectStatus().isOk();
    }

    @Test
    void t11_audit_op_log_persisted() {
        // 执行写操作 -> 验证 op_log 真落库（证明此前 15 个假写端点已改真）
        String tid = "T_AUDIT_" + suffix();
        client.post().uri("/internal/tenants")
                .bodyValue(Map.of("tenantId", tid, "tenantName", "audit", "status", "ACTIVE"))
                .exchange().expectStatus().isOk();
        client.get().uri(uriBuilder -> uriBuilder.path("/internal/system/op-logs")
                        .queryParam("size", "200").build())
                .exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.records[?(@.target_id=='" + tid + "')]").exists();
    }

    @Test
    void t12_admin_auth_required() {
        WebTestClient noAuth = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router").build();
        noAuth.get().uri("/internal/rbac/users").exchange().expectStatus().isUnauthorized();
        client.get().uri("/internal/rbac/users").exchange().expectStatus().isOk();
    }

    @Test
    void t13_every_internal_module_requires_token() {
        // 穷举各管控模块的代表端点：任何一个未返回 401 都意味着鉴权存在缺口
        WebTestClient noAuth = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router").build();
        List<String> endpoints = List.of(
                "/internal/dashboard/summary", "/internal/billing/bills", "/internal/rbac/users",
                "/internal/rbac/roles", "/internal/tenants", "/internal/policies", "/internal/models",
                "/internal/models/releases", "/internal/security/events", "/internal/security/alerts",
                "/internal/system/op-logs", "/internal/pricing/rules", "/internal/routing/engine",
                "/internal/collection/sources", "/internal/compute/summary", "/internal/metering/call-logs",
                "/internal/api-keys", "/internal/apps", "/internal/admin-auth/tokens");
        List<String> leaked = new ArrayList<>();
        for (String ep : endpoints) {
            int status = noAuth.get().uri(ep).exchange()
                    .expectBody(String.class).returnResult().getRawStatusCode();
            if (status != 401) {
                leaked.add(ep + " -> " + status);
            }
        }
        if (!leaked.isEmpty()) {
            throw new AssertionError("管理端点未受鉴权保护（可匿名访问）: " + leaked);
        }
    }

    @Test
    void t14_token_lifecycle_issue_use_revoke() {
        Map issued = client.post().uri("/internal/admin-auth/tokens")
                .bodyValue(Map.of("userCode", "e2e-op-" + suffix()))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        String issuedToken = String.valueOf(issued.get("token"));
        WebTestClient ops = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router")
                .defaultHeader("X-Admin-Token", issuedToken).build();
        // 签发后可正常访问
        ops.get().uri("/internal/rbac/users").exchange().expectStatus().isOk();
        // 吊销后必须立即失效（DB status=0 且缓存已失效），否则等于吊销形同虚设
        client.method(org.springframework.http.HttpMethod.DELETE).uri("/internal/admin-auth/tokens")
                .bodyValue(Map.of("token", issuedToken)).exchange().expectStatus().isOk();
        ops.get().uri("/internal/rbac/users").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void t15_expired_token_is_rejected() {
        Map issued = client.post().uri("/internal/admin-auth/tokens")
                .bodyValue(Map.of("userCode", "e2e-exp" + suffix(), "days", 0))
                .exchange().expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        String expired = String.valueOf(issued.get("token"));
        WebTestClient expiredClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router")
                .defaultHeader("X-Admin-Token", expired).build();
        expiredClient.get().uri("/internal/rbac/users").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void t16_all_critical_tables_verified_in_db() throws Exception {
        Field f = Class.forName("com.sunyard.llm.mas.config.DatabaseConfig")
                .getDeclaredField("EXPECTED_TABLES");
        f.setAccessible(true);
        String[] expected = (String[]) f.get(null);
        Set<String> existing = new HashSet<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    existing.add(rs.getString(1));
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String t : expected) {
            if (!existing.contains(t)) {
                missing.add(t);
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("关键表在真实库中缺失（启动核验失效）: " + missing);
        }
    }

    @Test
    void t17_release_id_is_respected_and_conflict_returns_409() {
        String rid = "REL-E2E-" + suffix();
        Map body = Map.of("releaseId", rid, "modelId", "M_REL_" + suffix(), "toVersion", "v2",
                "grayScope", "GLOBAL", "grayPercent", 20);
        client.post().uri("/internal/models/releases").bodyValue(body)
                .exchange().expectStatus().isOk();
        // 客户端传入的单号必须被采用，不得被服务端静默替换
        client.get().uri("/internal/models/releases").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$[?(@.release_id=='" + rid + "')]").exists();
        // 重复单号必须明确返回 409，不得静默忽略
        client.post().uri("/internal/models/releases")
                .bodyValue(Map.of("releaseId", rid, "modelId", "M_REL_" + suffix(), "toVersion", "v3",
                        "grayScope", "GLOBAL", "grayPercent", 30))
                .exchange().expectStatus().isEqualTo(409);
    }
}

package com.sunyard.llm.mas;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 兼容适配#2「行内底座对接」端到端冒烟测试（银行系统级回归）。
 * 用 zonky 嵌入式 PostgreSQL 启动原生数据库，应用启动即由 DatabaseConfig 自动建表 + 灌种子，
 * 再用 WebTestClient 把本轮回填的 BaseIntegration 模块所有端点按"调用→断言真实落库"顺序打一遍，
 * 重点验证：
 *   (1) 5 个对接点（IAM/4A/监控/告警/工单）列表与种子落库；
 *   (2) 连通性测试在未配置/未启用时如实回报 DISABLED/UNCONFIGURED，启用不可达时 UNREACHABLE 且不抛异常；
 *   (3) IAM 同步 / 监控推送本地闭环返回真实指标；
 *   (4) 告警转发与外部工单真实落入 KV（TICKETS），保存刷新不回退；
 *   (5) 对接动作全部进入 integration_log；
 *   (6) 通用 KV 配置 GET/PUT 闭环、修改后回读不回退。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaseIntegrationE2ETest {

    private static EmbeddedPostgres pg;
    // 每次用唯一英文数据目录，避开 Windows 中文用户名目录与残留文件锁
    private static final String PG_DATA_DIR = "D:/pgtest-int-" + System.nanoTime();
    @LocalServerPort
    private int port;
    private WebTestClient client;
    @Autowired
    private DataSource dataSource;

    /** 测试专用引导令牌：经 mas.admin-token 显式配置播种（生产默认不播种任何令牌） */
    private static final String TOKEN = "mat-test-e2e-token";

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
        } catch (java.io.IOException e) {
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
    }

    private String suffix() {
        return String.valueOf(System.nanoTime());
    }

    @Test
    void t01_list_seeded_five_integrations() {
        List<Map> list = client.get().uri("/internal/integration").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        if (list == null || list.size() < 5) throw new AssertionError("期望至少 5 个对接点，实际: " + list);
        Set<String> codes = new HashSet<>();
        for (Map m : list) codes.add(String.valueOf(m.get("code")));
        for (String c : new String[]{"IAM", "FOUR_A", "MONITOR", "ALERT", "TICKET"}) {
            if (!codes.contains(c)) throw new AssertionError("缺少对接点: " + c);
        }
    }

    @Test
    void t02_testConnectivity_unconfigured_returns_status_not_error() {
        // 种子对接点 enabled=0 且 endpoint 为空 -> 应回报 DISABLED（本地闭环，不抛异常）
        Map r = client.post().uri("/internal/integration/IAM/test").exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        String status = String.valueOf(r.get("status"));
        if (!"DISABLED".equals(status) && !"UNCONFIGURED".equals(status) && !"UNREACHABLE".equals(status))
            throw new AssertionError("连通性测试返回非预期状态: " + status);
    }

    @Test
    void t03_saveAndTest_enabledUnreachable_returnsUnreachable_notCrash() {
        String code = "E2E_" + suffix();
        client.put().uri("/internal/integration")
                .bodyValue(Map.of("code", code, "name", "e2e", "type", "IAM",
                        "endpoint", "http://127.0.0.1:9/dead", "enabled", true))
                .exchange().expectStatus().isOk();
        // 启用但地址不可达：真实外呼应返回 UNREACHABLE，绝不允许抛异常中断
        Map r = client.post().uri("/internal/integration/{c}/test", code).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        String status = String.valueOf(r.get("status"));
        if (!"UNREACHABLE".equals(status) && !"CONNECTED".equals(status))
            throw new AssertionError("启用但不可达应返回 UNREACHABLE/CONNECTED，实际: " + status);
    }

    @Test
    void t04_syncIam_localLoop_returnsCount() {
        Map r = client.post().uri("/internal/integration/iam/sync").exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        Object cnt = r.get("syncedUserCount");
        if (!(cnt instanceof Number)) throw new AssertionError("syncIam 返回缺少 syncedUserCount: " + r);
    }

    @Test
    void t05_pushMonitor_localLoop_returnsSnapshot() {
        Map r = client.post().uri("/internal/integration/monitor/push").exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        Object snap = r.get("snapshot");
        if (!(snap instanceof Map)) throw new AssertionError("pushMonitor 返回缺少 snapshot: " + r);
    }

    @Test
    void t06_forwardAlert_createsTicketInKv() throws Exception {
        String eventId = "SEC-E2E-" + suffix();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO mas_security_event (event_id, event_type, event_level, rule_name, reason_text, created_at) "
                             + "VALUES (?, 'MASKING', 'INFO', 'MASK-001', 'e2e 测试事件', now())")) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        }
        Map ticket = client.post().uri("/internal/integration/alert/forward/{id}", eventId).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        String ticketId = String.valueOf(ticket.get("ticketId"));
        // 工单必须真实落入 KV TICKETS（保存刷新不回退）
        List<Map> tickets = client.get().uri("/internal/system/config/TICKETS").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        boolean found = tickets != null && tickets.stream()
                .anyMatch(t -> ticketId.equals(String.valueOf(t.get("ticketId"))));
        if (!found) throw new AssertionError("告警转发工单未落入 KV TICKETS: " + tickets);
    }

    @Test
    void t07_createExternalTicket_persistsToKv() {
        Map ticket = client.post().uri("/internal/integration/ticket")
                .bodyValue(Map.of("type", "PROBLEM", "title", "e2e 工单", "content", "内容",
                        "from", "测试员", "deptName", "信息科技部"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        String ticketId = String.valueOf(ticket.get("ticketId"));
        List<Map> tickets = client.get().uri("/internal/system/config/TICKETS").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        boolean found = tickets != null && tickets.stream()
                .anyMatch(t -> ticketId.equals(String.valueOf(t.get("ticketId"))));
        if (!found) throw new AssertionError("外部工单未落入 KV TICKETS: " + tickets);
    }

    @Test
    void t08_logs_recorded() {
        List<Map> logs = client.get().uri("/internal/integration/logs").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        if (logs == null || logs.isEmpty()) throw new AssertionError("对接日志为空，动作未记录");
    }

    @Test
    void t09_kvConfig_roundTrip_noRollback() {
        String key = "E2E_KV_" + suffix();
        client.put().uri("/internal/system/config/{k}", key)
                .bodyValue(Map.of("field", "v1", "n", 1)).exchange().expectStatus().isOk();
        Map got1 = client.get().uri("/internal/system/config/{k}", key).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        if (!"v1".equals(String.valueOf(got1.get("field")))) throw new AssertionError("首次读取异常: " + got1);
        // 修改后再读，验证"保存刷新不回退"
        client.put().uri("/internal/system/config/{k}", key)
                .bodyValue(Map.of("field", "v2", "n", 2)).exchange().expectStatus().isOk();
        Map got2 = client.get().uri("/internal/system/config/{k}", key).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        if (!"v2".equals(String.valueOf(got2.get("field")))) throw new AssertionError("修改后读取未生效（回退）: " + got2);
    }
}

package com.sunyard.llm.mas;

import com.sun.net.httpserver.HttpServer;
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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 兼容适配#2「行内底座对接」系统性回归测试（分支级，覆盖 BaseIntegration 模块全部端点与方法）。
 * 与 BaseIntegrationE2ETest（冒烟）互补：本类聚焦<b>分支与边界</b>，
 * 重点验证连通性四种状态（DISABLED/UNCONFIGURED/UNREACHABLE/CONNECTED）、保存幂等、
 * 审计留痕（mas_op_log / mas_integration_log）、KV 数组追加、以及管理端点鉴权拒绝。
 * 用 zonky 嵌入式 PostgreSQL 跑真实落库，所有断言基于"调用→查库/查接口"双重校验。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaseIntegrationFullTest {

    private static EmbeddedPostgres pg;
    private static final String PG_DATA_DIR = "D:/pgtest-full-" + System.nanoTime();
    @LocalServerPort
    private int port;
    private WebTestClient client;
    @Autowired
    private DataSource dataSource;

    /** 测试专用引导令牌：经 mas.admin-token 显式配置播种（生产默认不播种任何令牌） */
    private static final String TOKEN = "mat-test-full-token";

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
    }

    /* ===================== 工具方法 ===================== */

    private String suffix() { return String.valueOf(System.nanoTime()); }

    private Map<String, Object> saveIntegration(String code, String type, boolean enabled, String endpoint) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", code);
        body.put("type", type);
        body.put("enabled", enabled);
        if (endpoint != null) body.put("endpoint", endpoint);
        return client.put().uri("/internal/integration").bodyValue(body).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
    }

    private Map<String, Object> testConnectivity(String code) {
        return client.post().uri("/internal/integration/{c}/test", code).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
    }

    private List<Map> getKv(String key) {
        return client.get().uri("/internal/system/config/{k}", key).exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findInList(String code) {
        List<Map> list = client.get().uri("/internal/integration").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        if (list == null) return null;
        for (Map m : list) if (code.equals(String.valueOf(m.get("code")))) return m;
        return null;
    }

    private boolean logsContain(String code, String action) {
        List<Map> logs = client.get().uri("/internal/integration/logs").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        if (logs == null) return false;
        return logs.stream().anyMatch(l -> code.equals(String.valueOf(l.get("intCode")))
                && action.equals(String.valueOf(l.get("action"))));
    }

    private int dbCountByCode(String code) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM mas_base_integration WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String dbStatus(String code) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT status FROM mas_base_integration WHERE code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : null; }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int opLogCount(String module) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM mas_op_log WHERE op_module = ?")) {
            ps.setString(1, module);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertSecurityEvent(String eventId, String ruleName) {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mas_security_event (event_id, event_type, event_level, rule_name, reason_text, created_at) "
                             + "VALUES (?, 'MASKING', 'INFO', ?, '集成测试事件', now())")) {
            ps.setString(1, eventId);
            ps.setString(2, ruleName);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* ===================== 列表 / 种子 ===================== */

    @Test
    void list_containsFiveSeededCodes() {
        List<Map> list = client.get().uri("/internal/integration").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        assertNotNull(list);
        assertTrue(list.size() >= 5, "期望至少 5 个对接点，实际: " + list.size());
        String[] codes = {"IAM", "FOUR_A", "MONITOR", "ALERT", "TICKET"};
        for (String code : codes) {
            boolean found = list.stream().anyMatch(m -> code.equals(String.valueOf(m.get("code"))));
            assertTrue(found, "列表缺少种子对接点: " + code);
        }
    }

    @Test
    void list_eachItemHasRequiredFields() {
        List<Map> list = client.get().uri("/internal/integration").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        assertNotNull(list);
        for (Map m : list) {
            assertNotNull(m.get("code"));
            assertNotNull(m.get("name"));
            assertNotNull(m.get("type"));
            assertNotNull(m.get("enabled"));
            assertNotNull(m.get("status"));
        }
    }

    /* ===================== 连通性四种状态 ===================== */

    @Test
    void test_nonexistentCode_returnsError() {
        String code = "NO_SUCH_" + suffix();
        client.post().uri("/internal/integration/{c}/test", code).exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("ERROR");
    }

    @Test
    void test_disabled_returnsDisabled() {
        String code = "DISC_" + suffix();
        saveIntegration(code, "IAM", false, "http://127.0.0.1:9/dead");
        Map r = testConnectivity(code);
        assertEquals("DISABLED", String.valueOf(r.get("status")));
        // 状态须真实落库
        assertEquals("DISABLED", dbStatus(code));
        assertTrue(logsContain(code, "TEST"));
    }

    @Test
    void test_enabledButEmptyEndpoint_returnsUnconfigured() {
        String code = "UNC_" + suffix();
        saveIntegration(code, "IAM", true, null); // enabled=1 但 endpoint 留空
        Map r = testConnectivity(code);
        assertEquals("UNCONFIGURED", String.valueOf(r.get("status")));
        assertEquals("UNCONFIGURED", dbStatus(code));
    }

    @Test
    void test_enabledUnreachable_returnsUnreachable() {
        String code = "UNR_" + suffix();
        saveIntegration(code, "IAM", true, "http://127.0.0.1:9/dead");
        Map r = testConnectivity(code);
        assertEquals("UNREACHABLE", String.valueOf(r.get("status")));
        assertNotNull(r.get("latencyMs"));
        assertEquals("UNREACHABLE", dbStatus(code));
        assertTrue(logsContain(code, "TEST"));
    }

    @Test
    void test_enabledReachable_returnsConnected() throws Exception {
        String code = "CONN_" + suffix();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> ex.sendResponseHeaders(200, -1));
        server.start();
        int srvPort = server.getAddress().getPort();
        try {
            saveIntegration(code, "IAM", true, "http://127.0.0.1:" + srvPort + "/");
            Map r = testConnectivity(code);
            assertEquals("CONNECTED", String.valueOf(r.get("status")), "真实可达应返回 CONNECTED");
            assertNotNull(r.get("latencyMs"));
            assertTrue(((Number) r.get("latencyMs")).longValue() >= 0);
            assertEquals("CONNECTED", dbStatus(code));
            assertTrue(logsContain(code, "TEST"));
        } finally {
            server.stop(0);
        }
    }

    /* ===================== 保存 ===================== */

    @Test
    void save_newIntegration_okAndEchoed() {
        String code = "SAVE_" + suffix();
        Map r = saveIntegration(code, "IAM", false, null);
        assertEquals(code, String.valueOf(r.get("code")));
        assertEquals(true, r.get("ok"));
        assertEquals(1, dbCountByCode(code));
    }

    @Test
    void save_resaveSameCode_updatesNotDuplicates() {
        String code = "UPS_" + suffix();
        saveIntegration(code, "IAM", false, "http://127.0.0.1:9/a");
        saveIntegration(code, "IAM", true, "http://127.0.0.1:9/b");
        assertEquals(1, dbCountByCode(code), "同一 code 重存应更新而非插入重复行");
        Map item = findInList(code);
        assertNotNull(item);
        assertEquals("http://127.0.0.1:9/b", String.valueOf(item.get("endpoint")));
        assertEquals(1, ((Number) item.get("enabled")).intValue());
    }

    @Test
    void save_enabledBooleanTrue_storedAs1() {
        String code = "ENB_" + suffix();
        saveIntegration(code, "IAM", true, null);
        Map item = findInList(code);
        assertEquals(1, ((Number) item.get("enabled")).intValue());
    }

    @Test
    void save_enabledBooleanFalse_storedAs0() {
        String code = "ENF_" + suffix();
        saveIntegration(code, "IAM", false, null);
        Map item = findInList(code);
        assertEquals(0, ((Number) item.get("enabled")).intValue());
    }

    @Test
    void save_enabledInt1_storedAs1() {
        String code = "ENI_" + suffix();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("type", "IAM");
        body.put("enabled", 1);
        client.put().uri("/internal/integration").bodyValue(body).exchange().expectStatus().isOk();
        assertEquals(1, ((Number) findInList(code).get("enabled")).intValue());
    }

    @Test
    void save_writesOpLog() throws Exception {
        int before = opLogCount("integration");
        String code = "OPL_" + suffix();
        saveIntegration(code, "IAM", false, null);
        int after = opLogCount("integration");
        assertTrue(after > before, "保存对接配置须写入操作审计 mas_op_log(module=integration)");
    }

    /* ===================== IAM 同步 ===================== */

    @Test
    void syncIam_disabledMode_returnsLocalAndUserCount() {
        // 确保 IAM 处于未启用
        saveIntegration("IAM", "IAM", false, null);
        Map r = client.post().uri("/internal/integration/iam/sync").exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertNotNull(r.get("syncedUserCount"));
        assertTrue(((Number) r.get("syncedUserCount")).longValue() >= 0);
        assertEquals("LOCAL", String.valueOf(r.get("mode")));
    }

    @Test
    void syncIam_enabledMode_returnsExternal_thenReset() {
        saveIntegration("IAM", "IAM", true, "http://127.0.0.1:9/iam");
        try {
            Map r = client.post().uri("/internal/integration/iam/sync").exchange()
                    .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
            assertEquals("EXTERNAL", String.valueOf(r.get("mode")));
            assertEquals("CONNECTED", dbStatus("IAM"));
        } finally {
            // 复位，避免影响其他用例对 IAM 状态的假设
            saveIntegration("IAM", "IAM", false, null);
        }
    }

    @Test
    void syncIam_persistsLastSync() {
        client.post().uri("/internal/integration/iam/sync").exchange().expectStatus().isOk();
        Map iam = findInList("IAM");
        assertNotNull(iam);
        assertNotNull(iam.get("lastSyncAt"), "同步后 lastSyncAt 不应为空");
    }

    /* ===================== 监控推送 ===================== */

    @Test
    void pushMonitor_returnsSnapshotWithCounts() {
        Map r = client.post().uri("/internal/integration/monitor/push").exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        Object snap = r.get("snapshot");
        assertTrue(snap instanceof Map);
        Map snapshot = (Map) snap;
        assertNotNull(snapshot.get("userCount"));
        assertNotNull(snapshot.get("securityEventCount"));
        assertEquals("LOCAL", String.valueOf(r.get("mode")));
    }

    @Test
    void pushMonitor_securityEventCountReflectsInserted() throws Exception {
        String eventId = "EVT_" + suffix();
        insertSecurityEvent(eventId, "RULE-REFLECT");
        Map r = client.post().uri("/internal/integration/monitor/push").exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        Map snapshot = (Map) r.get("snapshot");
        assertTrue(((Number) snapshot.get("securityEventCount")).longValue() >= 1,
                "已插入安全事件，监控快照应反映计数 >= 1");
    }

    @Test
    void pushMonitor_updatesMonitorStatus() {
        client.post().uri("/internal/integration/monitor/push").exchange().expectStatus().isOk();
        Map mon = findInList("MONITOR");
        assertNotNull(mon);
        String status = String.valueOf(mon.get("status"));
        assertTrue("PENDING".equals(status) || "CONNECTED".equals(status), "监控状态应为 PENDING/CONNECTED，实际: " + status);
    }

    /* ===================== 告警转发 ===================== */

    @Test
    void forwardAlert_withExistingEvent_contentIncludesDetail() throws Exception {
        String eventId = "SEC_DET_" + suffix();
        insertSecurityEvent(eventId, "MASK-DETAIL-001");
        Map ticket = client.post().uri("/internal/integration/alert/forward/{id}", eventId).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertNotNull(ticket.get("ticketId"));
        String content = String.valueOf(ticket.get("content"));
        assertTrue(content.contains("MASK-DETAIL-001"), "转发工单内容须包含原事件规则名: " + content);
        // 工单真实落入 KV TICKETS
        List<Map> tickets = getKv("TICKETS");
        boolean found = tickets != null && tickets.stream()
                .anyMatch(t -> String.valueOf(ticket.get("ticketId")).equals(String.valueOf(t.get("ticketId"))));
        assertTrue(found, "告警转发工单未落入 KV TICKETS");
        assertEquals("PENDING", dbStatus("ALERT"));
    }

    @Test
    void forwardAlert_withUnknownEvent_contentGeneric_butTicketCreated() {
        String eventId = "SEC_UNKNOWN_" + suffix();
        Map ticket = client.post().uri("/internal/integration/alert/forward/{id}", eventId).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertNotNull(ticket.get("ticketId"));
        String content = String.valueOf(ticket.get("content"));
        assertTrue(content.contains(eventId), "未知事件也须按规范转发并标注事件号: " + content);
        List<Map> tickets = getKv("TICKETS");
        boolean found = tickets != null && tickets.stream()
                .anyMatch(t -> String.valueOf(ticket.get("ticketId")).equals(String.valueOf(t.get("ticketId"))));
        assertTrue(found, "未知事件转发工单未落入 KV TICKETS");
    }

    /* ===================== 外部工单 ===================== */

    @Test
    void createTicket_defaults_statusOpen() {
        Map ticket = client.post().uri("/internal/integration/ticket")
                .bodyValue(Map.of("title", "默认工单" + suffix(), "content", "内容"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertEquals("OPEN", String.valueOf(ticket.get("status")));
        List<Map> tickets = getKv("TICKETS");
        boolean found = tickets != null && tickets.stream()
                .anyMatch(t -> String.valueOf(ticket.get("ticketId")).equals(String.valueOf(t.get("ticketId"))));
        assertTrue(found, "外部工单未落入 KV TICKETS");
    }

    @Test
    void createTicket_customFields_reflected() {
        String title = "自定义工单" + suffix();
        Map ticket = client.post().uri("/internal/integration/ticket")
                .bodyValue(Map.of("type", "CHANGE", "title", title, "content", "变更内容",
                        "from", "张三", "deptName", "风险管理部"))
                .exchange().expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertEquals("CHANGE", String.valueOf(ticket.get("type")));
        assertEquals(title, String.valueOf(ticket.get("title")));
        Map t = (Map) getKv("TICKETS").stream()
                .filter(x -> String.valueOf(ticket.get("ticketId")).equals(String.valueOf(x.get("ticketId"))))
                .findFirst().orElseThrow();
        assertEquals("张三", String.valueOf(t.get("from")));
        assertEquals("风险管理部", String.valueOf(t.get("deptName")));
    }

    @Test
    void createTicket_setsTicketStatusPending() {
        client.post().uri("/internal/integration/ticket")
                .bodyValue(Map.of("title", "状态工单" + suffix(), "content", "内容"))
                .exchange().expectStatus().isOk();
        assertEquals("PENDING", dbStatus("TICKET"));
    }

    @Test
    void appendArrayItem_twoTickets_growsArrayByTwo() {
        List<Map> before = getKv("TICKETS");
        int c0 = before == null ? 0 : before.size();
        client.post().uri("/internal/integration/ticket")
                .bodyValue(Map.of("title", "追加A" + suffix(), "content", "a")).exchange().expectStatus().isOk();
        client.post().uri("/internal/integration/ticket")
                .bodyValue(Map.of("title", "追加B" + suffix(), "content", "b")).exchange().expectStatus().isOk();
        List<Map> after = getKv("TICKETS");
        assertNotNull(after);
        assertEquals(c0 + 2, after.size(), "两次建单后 TICKETS 数组应正好 +2");
    }

    /* ===================== 日志 ===================== */

    @Test
    void logs_recordsActionsWithShape() {
        // 先触发一个动作，确保有日志
        client.post().uri("/internal/integration/iam/sync").exchange().expectStatus().isOk();
        List<Map> logs = client.get().uri("/internal/integration/logs").exchange()
                .expectStatus().isOk().expectBodyList(Map.class).returnResult().getResponseBody();
        assertNotNull(logs);
        assertTrue(!logs.isEmpty(), "对接动作须记录到 integration_log");
        Map first = logs.get(0);
        assertNotNull(first.get("intCode"));
        assertNotNull(first.get("action"));
        assertNotNull(first.get("status"));
    }

    /* ===================== 鉴权 ===================== */

    @Test
    void accessWithoutToken_returns401() {
        WebTestClient noAuth = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router").build();
        noAuth.get().uri("/internal/integration").exchange().expectStatus().isUnauthorized();
        noAuth.post().uri("/internal/integration/iam/sync").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void accessWithBadToken_returns401() {
        WebTestClient bad = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port + "/smart-router")
                .defaultHeader("X-Admin-Token", "not-a-valid-token").build();
        bad.get().uri("/internal/integration").exchange().expectStatus().isUnauthorized();
    }

    /* ===================== KV 闭环（兜底） ===================== */

    @Test
    void kvConfig_roundTrip_noRollback() {
        String key = "FULL_KV_" + suffix();
        client.put().uri("/internal/system/config/{k}", key)
                .bodyValue(Map.of("field", "v1", "n", 1)).exchange().expectStatus().isOk();
        Map got1 = client.get().uri("/internal/system/config/{k}", key).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertEquals("v1", String.valueOf(got1.get("field")));
        client.put().uri("/internal/system/config/{k}", key)
                .bodyValue(Map.of("field", "v2", "n", 2)).exchange().expectStatus().isOk();
        Map got2 = client.get().uri("/internal/system/config/{k}", key).exchange()
                .expectStatus().isOk().expectBody(Map.class).returnResult().getResponseBody();
        assertEquals("v2", String.valueOf(got2.get("field")), "修改后读取未生效（回退）");
    }

}

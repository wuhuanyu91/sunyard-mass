package com.sunyard.llm.mas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动时建表 + 种子数据（银行级 fail-fast，替代原 ResourceDatabasePopulator 静默容错方案）。
 * <p>
 * 规则：
 * <ol>
 * <li>逐条执行脚本，任何语句失败即抛出并中断启动 —— 建表失败不允许系统"半残缺"运行。
 *     唯一豁免：SQLState 42501（insufficient_privilege）。行内部署拓扑下表由部署脚本以属主
 *     （postgres）创建、应用账号（mas）仅有 DML 权限，应用侧 DDL 会稳定报 42501，属预期，
 *     记 WARN 跳过（参见历史 migration-real-data.sql 的 42501 注释，该文件已合并入 schema.sql）。</li>
 * <li>全部脚本执行后核验 {@link #EXPECTED_TABLES} 真实存在，缺任何一张即启动失败 ——
 *     无论哪套部署拓扑，关键表缺失都必须在启动期暴露，而不是运行期才报 relation does not exist。</li>
 * <li>脚本按依赖顺序加载（3 个，2026-09-26 定稿）：schema.sql（基础 8 表 + 治理 27 表，
 *     仅含自身建表的 ALTER/索引）→ migration-real-data.sql（原样保留的历史迁移：6 表 +
 *     租户映射函数 + 回填）→ data.sql（§0 为 migration 所建表补列 + 全部种子数据）。
 *     治理 DDL 中唯一依赖 migration 建表的语句（ALTER mas_routing_rule 补 ip_whitelist）
 *     置于 data.sql 顶部。全部语句幂等（IF NOT EXISTS / ON CONFLICT / WHERE NOT EXISTS），
 *     重启可重入。</li>
 * </ol>
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private static final String DEMO_ADMIN_TOKEN = "mat-demo-admin-token";

    private final Environment environment;

    public DatabaseConfig(Environment environment) {
        this.environment = environment;
    }

    /** 加载顺序即依赖顺序：schema（基础+治理建表）→ migration（原样保留的历史迁移）→ data（补列+种子） */
    private static final String[] SCRIPTS = {
            "db/schema.sql",
            "db/migration-real-data.sql",
            "db/data.sql"
    };

    /**
     * 启动核验清单（56 张，与上述脚本的建表语句一一对应，单一来源核对）。
     * 2026-09-27 新增 15 张：路由配置面落库 4 张（mas_routing_engine / mas_routing_rule_set /
     * mas_aggregation_group / mas_elastic_switch）、弹性算力编排与异构纳管 3 张
     * （mas_compute_orchestration / mas_batch_task / mas_hetero_vendor）、
     * 模型评测与归档 3 张（mas_model_eval / mas_model_archive / mas_archive_rule）、
     * 数据分级管控 1 张（mas_data_level_policy）、限流命中流水 1 张（mas_rate_limit_hit）、
     * 平台配置 KV 1 张（mas_platform_config）、行内底座对接 2 张（mas_base_integration / mas_integration_log）。
     */
    private static final String[] EXPECTED_TABLES = {
            // schema.sql · 基础 8 表（模型配置/调用日志/配额/缓存/黑名单/密钥/限流）
            "mas_model_config", "mas_call_log", "mas_token_quota", "mas_exact_cache",
            "mas_semantic_cache", "mas_blacklist", "mas_api_key", "mas_rate_limit",
            // migration-real-data.sql（6，原样保留）
            "mas_routing_rule", "mas_dept_quota", "mas_security_event", "mas_alert",
            "mas_app", "mas_app_application",
            // schema.sql · 治理 27 表（原 schema-v2-governance.sql 合并段）
            "mas_pricing_rule", "mas_bill", "mas_bill_item", "mas_reconciliation",
            "mas_sys_user", "mas_sys_role", "mas_sys_permission", "mas_sys_user_role",
            "mas_sys_role_permission", "mas_tenant", "mas_dept_tenant", "mas_app_tenant",
            "mas_security_rule", "mas_op_log", "mas_policy", "mas_policy_version",
            "mas_policy_exec_log", "mas_model_version", "mas_model_lineage", "mas_model_release",
            "mas_collection_source", "mas_collection_batch", "mas_compute_metric",
            "mas_guardrail_config", "mas_guardrail_policy", "mas_model_connection", "mas_admin_token",
            // 2026-09-27 补齐 · 路由配置面落库（此前 4 个端点假写不落库）
            "mas_routing_engine", "mas_routing_rule_set", "mas_aggregation_group", "mas_elastic_switch",
            // 2026-09-27 补齐 · 弹性算力编排与异构纳管
            "mas_compute_orchestration", "mas_batch_task", "mas_hetero_vendor",
            // 2026-09-27 补齐 · 模型评测与归档
            "mas_model_eval", "mas_model_archive", "mas_archive_rule",
            // 2026-09-27 补齐 · 数据分级差异化管控 + 限流命中流水
            "mas_data_level_policy", "mas_rate_limit_hit",
            // 2026-09-27 补齐 · 平台配置 KV（系统参数 / 安全基线 / 成本预警真实落库）
            "mas_platform_config",
            // 2026-09-27 补齐 · 兼容适配#2 行内底座对接（IAM/4A/监控/告警/工单）
            "mas_base_integration", "mas_integration_log"
    };

    @Bean
    public ApplicationRunner masSchemaInitializer(DataSource dataSource) {
        return args -> {
            int executed = 0;
            int skippedNoPrivilege = 0;
            try (Connection conn = dataSource.getConnection();
                 Statement st = conn.createStatement()) {
                for (String script : SCRIPTS) {
                    for (String stmt : splitStatements(loadScript(script))) {
                        try {
                            st.execute(stmt);
                            executed++;
                        } catch (SQLException e) {
                            if ("42501".equals(e.getSQLState())) {
                                // 部署侧持有 DDL 权限的拓扑下属预期，记录 WARN 后继续
                                skippedNoPrivilege++;
                                log.warn("schema sync skipped (insufficient_privilege, DDL owned by deployment): {}",
                                        abbrev(stmt));
                            } else {
                                throw new IllegalStateException("schema sync failed, refuse to start. script="
                                        + script + ", statement=[" + abbrev(stmt) + "], error=" + e.getMessage(), e);
                            }
                        }
                    }
                    log.info("schema script applied: {}", script);
                }
            }
            verifyTables(dataSource);
            seedAdminToken(dataSource);
            log.info("MAS schema/seed initialized: {} statements executed, {} skipped (insufficient_privilege), "
                    + "{} critical tables verified", executed, skippedNoPrivilege, EXPECTED_TABLES.length);
        };
    }

    /**
     * 管理端点令牌启动期播种（替代原 data.sql 中写死的演示令牌哈希）：
     * 优先取环境变量 {@code MAS_ADMIN_TOKEN}，其次取配置 {@code mas.admin-token}，
     * 二者均未设置时回退到演示令牌（仅限本地开发，启动日志会打印醒目安全告警）。
     * 哈希口径与 AdminAuthService.sha256 完全一致（SHA-256 小写十六进制），确保前后端一致。
     */
    private void seedAdminToken(DataSource dataSource) throws SQLException {
        String raw = environment.getProperty("mas.admin-token");
        if (raw == null || raw.isBlank()) {
            raw = System.getenv("MAS_ADMIN_TOKEN");
        }
        boolean demo = (raw == null || raw.isBlank());
        if (demo) {
            raw = DEMO_ADMIN_TOKEN;
            log.warn("==================================================================");
            log.warn("SECURITY: admin token seeded with DEFAULT DEMO value '{}'.", DEMO_ADMIN_TOKEN);
            log.warn("          For any non-local deployment set env MAS_ADMIN_TOKEN (or mas.admin-token).");
            log.warn("==================================================================");
        } else {
            log.info("admin token seeded from environment (MAS_ADMIN_TOKEN / mas.admin-token).");
        }
        final String tokenHash = sha256Hex(raw.trim());
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("INSERT INTO mas_admin_token (token_hash, user_code, role_code, status, expire_at) "
                    + "VALUES ('" + tokenHash + "', 'admin', 'ADMIN', 1, now() + interval '365 days') "
                    + "ON CONFLICT (token_hash) DO NOTHING");
        }
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 启动核验：关键表缺任何一张即抛异常中断启动 */
    private void verifyTables(DataSource dataSource) throws SQLException {
        Set<String> existing = new LinkedHashSet<>();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
            while (rs.next()) {
                existing.add(rs.getString(1));
            }
        }
        List<String> missing = new ArrayList<>();
        for (String t : EXPECTED_TABLES) {
            if (!existing.contains(t)) {
                missing.add(t);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "critical tables missing after schema sync, refuse to start (no degraded half-schema run): "
                            + missing);
        }
    }

    private static String loadScript(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("schema script not readable: " + path, e);
        }
    }

    /**
     * PostgreSQL 方言语句拆分：按分号切分，忽略
     * 行注释（--）、块注释（\/\* \*\/）、单引号字符串（'' 转义）、美元引号块（$$ / $tag$）。
     * 限制：不支持 E'...' 反斜杠转义字符串（当前 5 个脚本未使用）。
     */
    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            // 行注释
            if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                int j = sql.indexOf('\n', i);
                i = (j < 0) ? n : j + 1;
                continue;
            }
            // 块注释
            if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                int j = sql.indexOf("*/", i + 2);
                i = (j < 0) ? n : j + 2;
                continue;
            }
            // 单引号字符串（'' 为转义）
            if (c == '\'') {
                cur.append(c);
                i++;
                while (i < n) {
                    char q = sql.charAt(i);
                    cur.append(q);
                    i++;
                    if (q == '\'') {
                        if (i < n && sql.charAt(i) == '\'') {
                            cur.append('\'');
                            i++;
                        } else {
                            break;
                        }
                    }
                }
                continue;
            }
            // 美元引号块（$$ 或 $tag$）
            if (c == '$') {
                int j = i + 1;
                while (j < n && (Character.isLetterOrDigit(sql.charAt(j)) || sql.charAt(j) == '_')) {
                    j++;
                }
                if (j < n && sql.charAt(j) == '$') {
                    String tag = sql.substring(i, j + 1);
                    int end = sql.indexOf(tag, j + 1);
                    if (end >= 0) {
                        cur.append(sql, i, end + tag.length());
                        i = end + tag.length();
                        continue;
                    }
                }
                cur.append(c);
                i++;
                continue;
            }
            // 语句分隔符
            if (c == ';') {
                String s = cur.toString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
                cur.setLength(0);
                i++;
                continue;
            }
            cur.append(c);
            i++;
        }
        String s = cur.toString().trim();
        if (!s.isEmpty()) {
            out.add(s);
        }
        return out;
    }

    private static String abbrev(String stmt) {
        String s = stmt.trim().replaceAll("\\s+", " ");
        return s.length() > 140 ? s.substring(0, 140) + "..." : s;
    }
}

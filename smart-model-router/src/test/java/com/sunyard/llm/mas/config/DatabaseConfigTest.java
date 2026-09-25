package com.sunyard.llm.mas.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DatabaseConfig（银行 fail-fast 建表）专项测试 + 静态守卫。
 * <p>
 * 目标：证明"建表失败会中断启动"而非静默降级，并保证 SQL 与核验清单不漂移。
 */
class DatabaseConfigTest {

    private static final Pattern CREATE_TABLE =
            Pattern.compile("CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+([a-zA-Z_][a-zA-Z0-9_]*)",
                    Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("语句拆分：普通多语句")
    void splitSimpleStatements() {
        List<String> st = DatabaseConfig.splitStatements("SELECT 1; SELECT 2;");
        assertEquals(List.of("SELECT 1", "SELECT 2"), st);
    }

    @Test
    @DisplayName("语句拆分：行注释中的分号不得切分语句")
    void splitIgnoresSemicolonInLineComment() {
        List<String> st = DatabaseConfig.splitStatements("-- hello; world\n SELECT 1;");
        assertEquals(1, st.size(), "注释中的分号被误当作分隔符: " + st);
        assertTrue(st.get(0).contains("SELECT 1"));
    }

    @Test
    @DisplayName("语句拆分：块注释中的分号不得切分语句")
    void splitIgnoresSemicolonInBlockComment() {
        List<String> st = DatabaseConfig.splitStatements("/* a; b */ SELECT 1;");
        assertEquals(1, st.size(), "块注释中的分号被误切: " + st);
    }

    @Test
    @DisplayName("语句拆分：单引号字符串中的分号不得切分（含 '' 转义）")
    void splitRespectsStringLiterals() {
        List<String> st = DatabaseConfig.splitStatements(
                "INSERT INTO t(v) VALUES ('a;b'); INSERT INTO t(v) VALUES ('it''s;ok');");
        assertEquals(2, st.size(), "字符串内分号被误切: " + st);
        assertTrue(st.get(0).contains("'a;b'"));
        assertTrue(st.get(1).contains("'it''s;ok'"));
    }

    @Test
    @DisplayName("语句拆分：美元引号函数体整体保留（PL/pgSQL 依赖）")
    void splitRespectsDollarQuotedBlocks() {
        String sql = "CREATE OR REPLACE FUNCTION f() RETURNS int AS $$\n"
                + "BEGIN\n  RETURN 1; -- inside\nEND;\n$$ LANGUAGE plpgsql;";
        List<String> st = DatabaseConfig.splitStatements(sql);
        assertEquals(1, st.size(), "$$ 块被切碎会导致函数创建失败: " + st);
        assertTrue(st.get(0).contains("RETURN 1;"));
        assertTrue(st.get(0).endsWith("LANGUAGE plpgsql"));
    }

    @Test
    @DisplayName("语句拆分：带标签美元引号 $tag$ 同样保留")
    void splitRespectsTaggedDollarQuotes() {
        String sql = "CREATE FUNCTION g() RETURNS int AS $body$\nBEGIN\n RETURN 2;\nEND;\n$body$ LANGUAGE plpgsql;";
        assertEquals(1, DatabaseConfig.splitStatements(sql).size());
    }

    @Test
    @DisplayName("语句拆分：不产生空语句")
    void splitProducesNoEmptyStatements() {
        for (String s : DatabaseConfig.splitStatements("SELECT 1;;  -- x\n;SELECT 2;")) {
            assertFalse(s.trim().isEmpty());
        }
    }

    @Test
    @DisplayName("[fail-fast] 建表语句失败 -> 启动失败，不允许静默降级")
    void failFastOnNonPrivilegeError() {
        DataSource ds = failingDataSource("42P01");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DatabaseConfig(mockEnvironment()).masSchemaInitializer(ds).run(null));
        assertTrue(e.getMessage().contains("schema sync failed"),
                "非权限类错误必须中断启动，实际异常: " + e.getMessage());
    }

    @Test
    @DisplayName("[fail-fast] 42101/语法等任意错误同样中断启动")
    void failFastOnSyntaxError() {
        DataSource ds = failingDataSource("42601");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DatabaseConfig(mockEnvironment()).masSchemaInitializer(ds).run(null));
        assertTrue(e.getMessage().contains("schema sync failed"));
    }

    @Test
    @DisplayName("[权限豁免] 仅 SQLState 42501（行内部署 DDL 由运维持有）被降级为 WARN，且缺口仍被表核验兜住")
    void onlyInsufficientPrivilegeIsTolerated() {
        DataSource ds = failingDataSource("42501");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new DatabaseConfig(mockEnvironment()).masSchemaInitializer(ds).run(null));
        assertFalse(e.getMessage().contains("schema sync failed"),
                "42501 属预期拓扑，不应以 sync failed 中断");
        assertTrue(e.getMessage().contains("critical tables missing"),
                "即使豁免 DDL，关键表缺失仍必须中断启动");
    }

    @Test
    @DisplayName("[启动核验清单守卫] EXPECTED_TABLES 与 3 个 SQL 脚本的建表语句完全一致，不漂移")
    void expectedTablesMatchSqlDefinitions() throws Exception {
        Field scriptsField = DatabaseConfig.class.getDeclaredField("SCRIPTS");
        scriptsField.setAccessible(true);
        String[] scripts = (String[]) scriptsField.get(null);

        Field tablesField = DatabaseConfig.class.getDeclaredField("EXPECTED_TABLES");
        tablesField.setAccessible(true);
        Set<String> expected = new LinkedHashSet<>(List.of((String[]) tablesField.get(null)));
        assertEquals(expected.size(), ((String[]) tablesField.get(null)).length,
                "EXPECTED_TABLES 存在重复项");

        Set<String> declared = new HashSet<>();
        for (String script : scripts) {
            declared.addAll(parseCreateTables(read(script)));
        }
        assertEquals(expected, declared,
                "启动核验清单与 SQL 建表不一致：新增/删除建表语句后必须同步 EXPECTED_TABLES");
    }

    @Test
    @DisplayName("[单一来源] mas_security_event / mas_alert 只允许定义一次，避免结构漂移")
    void securityEventTablesHaveSingleDefinition() throws Exception {
        for (String table : List.of("mas_security_event", "mas_alert")) {
            int count = 0;
            for (String script : DatabaseConfigSCRIPTS) {
                count += occurrences(read(script), "CREATE TABLE IF NOT EXISTS " + table);
            }
            assertEquals(1, count, table + " 存在 " + count + " 处 CREATE TABLE 定义，必须唯一");
        }
    }

    @Test
    @DisplayName("[鉴权前缀守卫] 不存在裸 /internal 映射，杜绝未来新增绕过鉴权的端点")
    void noBareInternalMapping() throws Exception {
        List<String> bare = new ArrayList<>();
        for (String path : scanMappingPaths()) {
            if (path.startsWith("/internal") && !path.startsWith("/internal/")) {
                bare.add(path);
            }
        }
        assertTrue(bare.isEmpty(),
                "存在不以 /internal/ 开头的管理类映射，将绕过 AdminAuthFilter: " + bare);
    }

    // ------------------------------------------------------------------ helpers

    private static final String[] DatabaseConfigSCRIPTS = loadScriptsField();

    private static String[] loadScriptsField() {
        try {
            Field f = DatabaseConfig.class.getDeclaredField("SCRIPTS");
            f.setAccessible(true);
            return (String[]) f.get(null);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 遍历 src/main/java 收集所有 @XxxMapping 的路径字面量 */
    private static List<String> scanMappingPaths() throws IOException {
        Path root = Paths.get("src/main/java");
        List<String> paths = new ArrayList<>();
        Pattern p = Pattern.compile(
                "@(?:Get|Post|Put|Delete|Patch|Request)Mapping\\(\\s*\"([^\"]+)\"");
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path f : walk.filter(p2 -> p2.toString().endsWith(".java")).toList()) {
                Matcher m = p.matcher(Files.readString(f, StandardCharsets.UTF_8));
                while (m.find()) {
                    paths.add(m.group(1));
                }
            }
        }
        return paths;
    }

    private static Set<String> parseCreateTables(String sql) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = CREATE_TABLE.matcher(sql);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static int occurrences(String sql, String needle) {
        // 忽略大小写与多余空白做宽松匹配
        String flat = sql.toLowerCase().replaceAll("\\s+", " ");
        String target = needle.toLowerCase();
        int count = 0, idx = 0;
        while ((idx = flat.indexOf(target, idx)) >= 0) {
            count++;
            idx += target.length();
        }
        return count;
    }

    private static String read(String classpath) throws IOException {
        return new String(new ClassPathResource(classpath).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    /** 最小 Environment 桩：属性均返回 null（测试场景下回落到演示令牌），不影响 fail-fast 断言 */
    private static Environment mockEnvironment() {
        return (Environment) Proxy.newProxyInstance(
                DatabaseConfigTest.class.getClassLoader(), new Class<?>[]{Environment.class},
                (p, m, a) -> {
                    switch (m.getName()) {
                        case "getProperty" -> {
                            if (a.length >= 2 && a[1] instanceof String s) return s;
                            return null;
                        }
                        case "containsProperty" -> { return false; }
                        case "getActiveProfiles", "getDefaultProfiles" -> { return new String[0]; }
                        case "acceptsProfiles" -> { return false; }
                        default -> { return null; }
                    }
                });
    }

    /** 构造"执行任意语句都失败"的数据源，用于验证 fail-fast */
    private static DataSource failingDataSource(String sqlState) {
        ResultSet emptyRs = (ResultSet) Proxy.newProxyInstance(
                DatabaseConfigTest.class.getClassLoader(), new Class<?>[]{ResultSet.class},
                (p, m, a) -> switch (m.getName()) {
                    case "next" -> false;
                    case "close" -> null;
                    default -> defaultValue(m.getReturnType());
                });

        Statement st = (Statement) Proxy.newProxyInstance(
                DatabaseConfigTest.class.getClassLoader(), new Class<?>[]{Statement.class},
                (p, m, a) -> switch (m.getName()) {
                    case "executeQuery" -> emptyRs;
                    case "execute" -> throw new SQLException("simulated failure", sqlState);
                    case "close" -> null;
                    default -> defaultValue(m.getReturnType());
                });

        Connection conn = (Connection) Proxy.newProxyInstance(
                DatabaseConfigTest.class.getClassLoader(), new Class<?>[]{Connection.class},
                (p, m, a) -> switch (m.getName()) {
                    case "createStatement" -> st;
                    case "close" -> null;
                    default -> defaultValue(m.getReturnType());
                });

        return (DataSource) Proxy.newProxyInstance(
                DatabaseConfigTest.class.getClassLoader(), new Class<?>[]{DataSource.class},
                (p, m, a) -> switch (m.getName()) {
                    case "getConnection" -> conn;
                    default -> defaultValue(m.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == void.class) return null;
        return null;
    }
}

package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.DataLevelPolicyEntity;
import com.sunyard.llm.mas.mapper.DataLevelPolicyMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 数据分级差异化管控（招标二-5 数据保护）：
 * data_level（L1 公开 / L2 内部 / L3 敏感）此前只是 mas_call_log 上的一个字段，
 * 平台没有任何按等级执行的管控逻辑。本服务把等级转成真正生效的规则：
 * <ul>
 *   <li>可用部署形态：L3 禁止出本地自建算力（禁云端 / 禁租赁）</li>
 *   <li>脱敏强度：L3 走 STRICT（身份证/手机号/银行卡号打码），L1/L2 走 STANDARD</li>
 *   <li>内容留存：L3 默认不留存请求响应原文，只留哈希与元数据</li>
 *   <li>上下文上限：按等级限制最大上下文长度</li>
 * </ul>
 * 策略 60s 缓存，支持管理面在线改。
 */
@Service
public class DataLevelPolicyService {

    private static final Logger log = LoggerFactory.getLogger(DataLevelPolicyService.class);
    private static final long CACHE_TTL_MS = 60_000L;

    /** 身份证号（15/18 位，末位可为 X） */
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)\\d{6}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx](?!\\d)");
    /** 手机号 */
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    /** 银行卡号（16~19 位） */
    private static final Pattern BANK_CARD = Pattern.compile("(?<!\\d)\\d{16,19}(?!\\d)");

    private final DataLevelPolicyMapper mapper;
    private final SensitiveWordFilter sensitiveWordFilter;

    private final ConcurrentHashMap<String, DataLevelPolicyEntity> cache = new ConcurrentHashMap<>();
    private volatile long loadedAt = 0L;

    public DataLevelPolicyService(DataLevelPolicyMapper mapper, SensitiveWordFilter sensitiveWordFilter) {
        this.mapper = mapper;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    // ---------------- 策略读取 ----------------

    public List<Map<String, Object>> listPolicies() {
        reloadIfStale();
        return cache.values().stream()
                .sorted((a, b) -> String.valueOf(a.getDataLevel()).compareTo(String.valueOf(b.getDataLevel())))
                .map(this::toMap)
                .toList();
    }

    public Map<String, Object> savePolicy(Map<String, Object> body, String operator) {
        // data_level 必填：String.valueOf(null) 会生成 "null" 等级的脏策略行
        Object rawLevel = body.getOrDefault("data_level", body.get("dataLevel"));
        if (rawLevel == null || String.valueOf(rawLevel).isBlank()) {
            throw new IllegalArgumentException("data_level 必填（L1/L2/L3）");
        }
        String level = String.valueOf(rawLevel).trim().toUpperCase();
        DataLevelPolicyEntity e = mapper.selectById(level);
        boolean insert = e == null;
        if (insert) {
            e = new DataLevelPolicyEntity();
            e.setDataLevel(level);
        }
        if (body.get("allow_local") != null) e.setAllowLocal(flag(body.get("allow_local")));
        if (body.get("allowLocal") != null) e.setAllowLocal(flag(body.get("allowLocal")));
        if (body.get("allow_cloud") != null) e.setAllowCloud(flag(body.get("allow_cloud")));
        if (body.get("allowCloud") != null) e.setAllowCloud(flag(body.get("allowCloud")));
        if (body.get("allow_rental") != null) e.setAllowRental(flag(body.get("allow_rental")));
        if (body.get("allowRental") != null) e.setAllowRental(flag(body.get("allowRental")));
        Object ms = body.getOrDefault("mask_strength", body.get("maskStrength"));
        if (ms != null) e.setMaskStrength(String.valueOf(ms));
        Object cr = body.getOrDefault("content_retention", body.get("contentRetention"));
        if (cr != null) e.setContentRetention(flag(cr));
        Object rd = body.getOrDefault("retention_days", body.get("retentionDays"));
        if (rd != null) e.setRetentionDays(intVal(rd));
        Object mc = body.getOrDefault("max_context_tokens", body.get("maxContextTokens"));
        if (mc != null) e.setMaxContextTokens(intVal(mc));
        e.setUpdatedBy(operator);
        if (insert) {
            mapper.insert(e);
        } else {
            mapper.updateById(e);
        }
        reloadIfStale(true);
        log.info("data level policy {} updated by {}", level, operator);
        return toMap(e);
    }

    // ---------------- 运行时判定 ----------------

    /** 某数据等级是否允许使用指定部署形态承载（LOCAL / CLOUD / RENTAL） */
    public boolean allowDeployType(String dataLevel, String deployType) {
        if (deployType == null || deployType.isBlank()) return true;
        DataLevelPolicyEntity p = policyOf(dataLevel);
        if (p == null) return true;
        return switch (deployType.trim().toUpperCase()) {
            case "LOCAL" -> p.getAllowLocal() == null || p.getAllowLocal() == 1;
            case "CLOUD" -> p.getAllowCloud() == null || p.getAllowCloud() == 1;
            case "RENTAL" -> p.getAllowRental() == null || p.getAllowRental() == 1;
            default -> true;
        };
    }

    /** 该等级是否允许留存请求/响应原文 */
    public boolean contentRetained(String dataLevel) {
        DataLevelPolicyEntity p = policyOf(dataLevel);
        return p == null || p.getContentRetention() == null || p.getContentRetention() == 1;
    }

    /** 按等级对内容实施对应强度的脱敏 */
    public String mask(String content, String dataLevel) {
        if (content == null || content.isEmpty()) return content;
        String strength = policyOf(dataLevel) == null ? "STANDARD" : policyOf(dataLevel).getMaskStrength();
        String masked = sensitiveWordFilter == null ? content : sensitiveWordFilter.mask(content);
        if ("STRICT".equalsIgnoreCase(strength)) {
            masked = maskPii(masked);
        } else if ("NONE".equalsIgnoreCase(strength)) {
            return content;
        }
        return masked;
    }

    /** 该等级的上下文长度上限（未配置返回 0 = 不限制） */
    public int maxContextTokens(String dataLevel) {
        DataLevelPolicyEntity p = policyOf(dataLevel);
        return p == null || p.getMaxContextTokens() == null ? 0 : p.getMaxContextTokens();
    }

    public int retentionDays(String dataLevel) {
        DataLevelPolicyEntity p = policyOf(dataLevel);
        return p == null || p.getRetentionDays() == null ? 180 : p.getRetentionDays();
    }

    // ---------------- helpers ----------------

    /** 强脱敏：身份证 / 手机号 / 银行卡号打码 */
    public static String maskPii(String text) {
        if (text == null || text.isEmpty()) return text;
        String r = ID_CARD.matcher(text).replaceAll(m -> keep(m.group(), 6, 4));
        r = BANK_CARD.matcher(r).replaceAll(m -> keep(m.group(), 6, 4));
        r = MOBILE.matcher(r).replaceAll(m -> keep(m.group(), 3, 4));
        return r;
    }

    /** 保留首尾若干位，中间打星 */
    private static String keep(String s, int head, int tail) {
        int len = s.length();
        if (len <= head + tail) return "*".repeat(len);
        return s.substring(0, head) + "*".repeat(len - head - tail) + s.substring(len - tail);
    }

    private DataLevelPolicyEntity policyOf(String dataLevel) {
        if (dataLevel == null || dataLevel.isBlank()) return null;
        reloadIfStale();
        return cache.get(dataLevel.trim().toUpperCase());
    }

    private synchronized void reloadIfStale() {
        reloadIfStale(false);
    }

    private synchronized void reloadIfStale(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - loadedAt < CACHE_TTL_MS && !cache.isEmpty()) return;
        try {
            List<DataLevelPolicyEntity> rows = mapper.selectList(null);
            ConcurrentHashMap<String, DataLevelPolicyEntity> fresh = new ConcurrentHashMap<>();
            for (DataLevelPolicyEntity e : rows) {
                fresh.put(String.valueOf(e.getDataLevel()).trim().toUpperCase(), e);
            }
            cache.clear();
            cache.putAll(fresh);
            loadedAt = now;
        } catch (Exception ex) {
            log.warn("data level policy reload failed (keep previous): {}", ex.getMessage());
        }
    }

    private Map<String, Object> toMap(DataLevelPolicyEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("data_level", e.getDataLevel());
        m.put("allow_local", e.getAllowLocal());
        m.put("allow_cloud", e.getAllowCloud());
        m.put("allow_rental", e.getAllowRental());
        m.put("mask_strength", e.getMaskStrength());
        m.put("content_retention", e.getContentRetention());
        m.put("retention_days", e.getRetentionDays());
        m.put("max_context_tokens", e.getMaxContextTokens());
        return m;
    }

    private static int flag(Object v) {
        if (v == null) return 0;
        if (v instanceof Boolean b) return b ? 1 : 0;
        if (v instanceof Number n) return n.intValue() != 0 ? 1 : 0;
        return Boolean.parseBoolean(String.valueOf(v)) ? 1 : 0;
    }

    private static int intVal(Object v) {
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }
}

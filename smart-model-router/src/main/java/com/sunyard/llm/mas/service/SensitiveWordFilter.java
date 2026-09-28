package com.sunyard.llm.mas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.mapper.GuardrailMapper;
import com.sunyard.llm.mas.mapper.PlatformConfigMapper;
import com.sunyard.llm.mas.entity.PlatformConfigEntity;
import jakarta.annotation.PostConstruct;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 敏感词过滤（Aho-Corasick 多模式匹配）。
 * L1 输入检查与 L4 输出审核共用（附录 E.9）；词表缺失/为空时全部跳过。
 * <p>
 * 【改造背景】此前词表只认资源文件 sensitive-words.txt，管理面护栏策略表
 * （mas_guardrail_policy）的 stage/action/keyword_lib 配置完全不被运行时消费。
 * 现合并两个词源：
 * 1. 资源文件基础词表（启动加载）；
 * 2. 护栏策略在线词库（每 60s 刷新）：启用状态策略的 keyword_lib 字段支持
 *    内联词表（逗号/分号/换行分隔）或词库 ID（查 mas_platform_config.KEYWORD_LIBS
 *    条目内的 words 数组），命中即并入 AC 自动机。
 * DB 不可用时保持现有词表继续生效（降级不失效）。
 */
@Service
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);
    private static final String MASK = "***";
    private static final String KEYWORD_LIBS_CONFIG_KEY = "KEYWORD_LIBS";
    private static final ObjectMapper OM = new ObjectMapper();

    private final String resourcePath;
    private final GuardrailMapper guardrailMapper;
    private final PlatformConfigMapper configMapper;
    private volatile Trie trie;
    /** 当前生效词数（刷新时对比，词数有变化才重建自动机） */
    private volatile int currentCount = 0;
    /** 资源文件基础词表（DB 词库刷新时与之合并） */
    private volatile List<String> baseWords = List.of();

    public SensitiveWordFilter(MasProperties props,
                               GuardrailMapper guardrailMapper,
                               PlatformConfigMapper configMapper) {
        this.resourcePath = props.getSensitiveWords().getPath();
        this.guardrailMapper = guardrailMapper;
        this.configMapper = configMapper;
    }

    @PostConstruct
    public void init() {
        baseWords = loadWords(resourcePath);
        rebuild(baseWords);
    }

    /**
     * 护栏词库在线刷新（60s 周期）：读取启用的护栏策略词表并与基础词表合并。
     * 异常时保留当前词表（fail-static），仅告警。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 15_000)
    public void refreshGuardrailWords() {
        if (guardrailMapper == null) {
            return;
        }
        try {
            Set<String> merged = new LinkedHashSet<>(baseWords);
            int policies = 0;
            for (Map<String, Object> p : guardrailMapper.listPolicies()) {
                Object enabled = p.get("enabled");
                if (enabled == null || Integer.parseInt(String.valueOf(enabled)) != 1) {
                    continue;
                }
                String keywordLib = p.get("keyword_lib") == null ? "" : String.valueOf(p.get("keyword_lib"));
                if (keywordLib.isBlank()) {
                    continue;
                }
                List<String> words = resolveWords(keywordLib.trim());
                if (!words.isEmpty()) {
                    merged.addAll(words);
                    policies++;
                }
            }
            if (merged.size() != currentCount) {
                rebuild(List.copyOf(merged));
                log.info("guardrail keyword libs refreshed: {} policy libs, {} total words", policies, merged.size());
            }
        } catch (Exception e) {
            log.warn("guardrail keyword refresh degraded (keep current trie): {}", e.getMessage());
        }
    }

    /** 解析 keyword_lib 字段：内联词表（含分隔符）直接拆词；否则按词库 ID 查 KEYWORD_LIBS */
    private List<String> resolveWords(String keywordLib) {
        if (keywordLib.contains(",") || keywordLib.contains("；") || keywordLib.contains(";")
                || keywordLib.contains("\n") || keywordLib.contains("，")) {
            List<String> words = new ArrayList<>();
            for (String w : keywordLib.split("[,，;；\\n]")) {
                if (!w.isBlank()) {
                    words.add(w.trim());
                }
            }
            return words;
        }
        // 词库 ID：查 mas_platform_config.KEYWORD_LIBS 条目内的 words 数组
        if (configMapper == null) {
            return List.of();
        }
        try {
            PlatformConfigEntity cfg = configMapper.selectByKey(KEYWORD_LIBS_CONFIG_KEY);
            if (cfg == null || cfg.getConfigJson() == null) {
                return List.of();
            }
            JsonNode arr = OM.readTree(cfg.getConfigJson());
            if (!arr.isArray()) {
                return List.of();
            }
            for (JsonNode lib : arr) {
                String libId = lib.path("libId").asText("");
                if (!keywordLib.equals(libId)) {
                    continue;
                }
                List<String> words = new ArrayList<>();
                JsonNode ws = lib.get("words");
                if (ws != null && ws.isArray()) {
                    for (JsonNode w : ws) {
                        if (w.isTextual() && !w.asText().isBlank()) {
                            words.add(w.asText().trim());
                        }
                    }
                }
                return words;
            }
        } catch (Exception e) {
            log.debug("keyword lib '{}' not resolved: {}", keywordLib, e.getMessage());
        }
        return List.of();
    }

    /** 供测试直接注入词表 */
    void rebuild(List<String> words) {
        if (words == null || words.isEmpty()) {
            this.trie = null;
            this.currentCount = 0;
            log.info("Sensitive word list is empty, filter disabled");
            return;
        }
        Trie.TrieBuilder builder = Trie.builder();
        int count = 0;
        for (String w : words) {
            if (w != null && !w.isBlank()) {
                builder.addKeyword(w.trim());
                count++;
            }
        }
        this.trie = builder.build();
        this.currentCount = count;
        log.info("Sensitive word filter loaded, {} words", count);
    }

    private List<String> loadWords(String path) {
        try {
            Resource resource = new DefaultResourceLoader().getResource(path);
            if (!resource.exists()) {
                return List.of();
            }
            List<String> words = new ArrayList<>();
            try (InputStream in = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        words.add(line.trim());
                    }
                }
            }
            return words;
        } catch (Exception e) {
            log.warn("Failed to load sensitive words from {}: {}", path, e.getMessage());
            return List.of();
        }
    }

    public boolean contains(String text) {
        Trie t = this.trie;
        if (t == null || text == null || text.isEmpty()) {
            return false;
        }
        return !t.parseText(text).isEmpty();
    }

    /** 输出审核脱敏：命中词替换为 *** */
    public String mask(String text) {
        Trie t = this.trie;
        if (t == null || text == null || text.isEmpty()) {
            return text;
        }
        Collection<Emit> emits = t.parseText(text);
        if (emits.isEmpty()) {
            return text;
        }
        List<Emit> sorted = emits.stream()
                .sorted(Comparator.comparingInt(Emit::getStart))
                .toList();
        StringBuilder sb = new StringBuilder(text.length());
        int cursor = 0;
        for (Emit emit : sorted) {
            if (emit.getStart() < cursor) {
                continue; // 跳过重叠区间
            }
            sb.append(text, cursor, emit.getStart()).append(MASK);
            cursor = emit.getEnd() + 1;
        }
        sb.append(text, cursor, text.length());
        return sb.toString();
    }
}

package com.sunyard.llm.mas.service;

import jakarta.annotation.PostConstruct;
import org.ahocorasick.trie.Emit;
import org.ahocorasick.trie.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 敏感词过滤（Aho-Corasick 多模式匹配）。
 * L1 输入检查与 L4 输出审核共用（附录 E.9）；词表缺失/为空时全部跳过。
 */
@Service
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);
    private static final String MASK = "***";

    private final String resourcePath;
    private volatile Trie trie;

    public SensitiveWordFilter(com.sunyard.llm.mas.config.MasProperties props) {
        this.resourcePath = props.getSensitiveWords().getPath();
    }

    @PostConstruct
    public void init() {
        List<String> words = loadWords(resourcePath);
        rebuild(words);
    }

    /** 供测试直接注入词表 */
    void rebuild(List<String> words) {
        if (words == null || words.isEmpty()) {
            this.trie = null;
            log.info("Sensitive word list is empty, filter disabled");
            return;
        }
        Trie.TrieBuilder builder = Trie.builder();
        for (String w : words) {
            if (w != null && !w.isBlank()) {
                builder.addKeyword(w.trim());
            }
        }
        this.trie = builder.build();
        log.info("Sensitive word filter loaded, {} words", words.size());
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

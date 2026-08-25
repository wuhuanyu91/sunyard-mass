package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC 自动机敏感词匹配与脱敏（附录 E.9）。
 */
class SensitiveWordFilterTest {

    private SensitiveWordFilter newFilter(List<String> words) {
        SensitiveWordFilter filter = new SensitiveWordFilter(new MasProperties());
        filter.rebuild(words);
        return filter;
    }

    @Test
    void containsMatchesMultiPattern() {
        SensitiveWordFilter filter = newFilter(List.of("测试敏感词", "违法违规"));
        assertTrue(filter.contains("这是一段包含测试敏感词的文本"));
        assertTrue(filter.contains("内容违法违规"));
        assertFalse(filter.contains("完全正常的文本"));
    }

    @Test
    void maskReplacesHits() {
        SensitiveWordFilter filter = newFilter(List.of("敏感A", "敏感B"));
        assertEquals("前缀***后缀", filter.mask("前缀敏感A后缀"));
        assertEquals("普通文本", filter.mask("普通文本"));
    }

    @Test
    void emptyWordListDisablesFilter() {
        SensitiveWordFilter filter = newFilter(List.of());
        assertFalse(filter.contains("任何文本"));
        assertEquals("任何文本", filter.mask("任何文本"));
    }
}

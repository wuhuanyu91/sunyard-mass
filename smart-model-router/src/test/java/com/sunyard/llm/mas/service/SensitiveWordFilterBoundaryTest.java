package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AC 自动机敏感词过滤边界测试：null/空输入、整词/连续命中、
 * 重叠区间处理、词表清洗（trim/null/空白行）、大小写敏感性。
 */
class SensitiveWordFilterBoundaryTest {

    private SensitiveWordFilter newFilter(List<String> words) {
        SensitiveWordFilter filter = new SensitiveWordFilter(new MasProperties());
        filter.rebuild(words);
        return filter;
    }

    // ---- contains/mask 的 null 与空输入 ----

    @Test
    void containsReturnsFalseForNullOrEmptyText() {
        SensitiveWordFilter filter = newFilter(List.of("禁词"));
        assertFalse(filter.contains(null));
        assertFalse(filter.contains(""));
    }

    @Test
    void maskReturnsInputUnchangedForNullOrEmpty() {
        SensitiveWordFilter filter = newFilter(List.of("禁词"));
        assertNull(filter.mask(null));
        assertEquals("", filter.mask(""));
    }

    // ---- 命中位置边界 ----

    @Test
    void hitAtTextStart() {
        SensitiveWordFilter filter = newFilter(List.of("禁词"));
        assertEquals("***开头", filter.mask("禁词开头"));
    }

    @Test
    void hitAtTextEnd() {
        SensitiveWordFilter filter = newFilter(List.of("禁词"));
        assertEquals("结尾***", filter.mask("结尾禁词"));
    }

    @Test
    void wholeTextIsTheWord() {
        SensitiveWordFilter filter = newFilter(List.of("禁词"));
        assertEquals("***", filter.mask("禁词"));
        assertTrue(filter.contains("禁词"));
    }

    @Test
    void consecutiveHitsAllMasked() {
        SensitiveWordFilter filter = newFilter(List.of("敏感A", "敏感B"));
        assertEquals("******", filter.mask("敏感A敏感B"));
    }

    @Test
    void multipleHitsPreserveSurroundingText() {
        SensitiveWordFilter filter = newFilter(List.of("坏词"));
        assertEquals("前***中***后", filter.mask("前坏词中坏词后"));
    }

    // ---- 重叠区间 ----

    @Test
    void overlappingWordsLongerEmitMaskedFirst() {
        // "abc" 与 "bc" 重叠：按起始位排序后先处理 abc，bc 被跳过
        SensitiveWordFilter filter = newFilter(Arrays.asList("abc", "bc"));
        assertEquals("***", filter.mask("abc"));
    }

    @Test
    void overlappingEmitsSkippedAfterFirstMask() {
        // "ab" 命中 [0,1] 后游标=2，"bc" 起始位 1 < 2 被跳过
        SensitiveWordFilter filter = newFilter(Arrays.asList("ab", "bc"));
        assertEquals("***cd", filter.mask("abcd"));
    }

    // ---- 词表清洗 ----

    @Test
    void wordsAreTrimmedBeforeMatching() {
        SensitiveWordFilter filter = newFilter(List.of("  禁词  "));
        assertTrue(filter.contains("文本含禁词"));
        assertEquals("***", filter.mask("禁词"));
    }

    @Test
    void nullAndBlankWordsAreIgnoredWithoutError() {
        SensitiveWordFilter filter = newFilter(Arrays.asList(null, "   ", "有效词"));
        assertTrue(filter.contains("这里有有效词"));
        assertFalse(filter.contains("没有命中"));
    }

    @Test
    void rebuildWithNullDisablesFilter() {
        SensitiveWordFilter filter = newFilter(List.of("禁词"));
        assertTrue(filter.contains("禁词"));
        filter.rebuild(null);
        assertFalse(filter.contains("禁词"));
        assertEquals("禁词", filter.mask("禁词"));
    }

    // ---- 大小写敏感性（AC 按词表原样匹配） ----

    @Test
    void matchingIsCaseSensitiveForAsciiWords() {
        SensitiveWordFilter filter = newFilter(List.of("Secret"));
        assertTrue(filter.contains("my Secret"));
        assertFalse(filter.contains("my secret"));
    }
}

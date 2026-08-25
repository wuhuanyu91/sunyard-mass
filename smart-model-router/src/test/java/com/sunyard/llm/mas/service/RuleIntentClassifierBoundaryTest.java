package com.sunyard.llm.mas.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 规则意图分类边界测试：null/空白兜底、code 与 rag 优先级、
 * 全部关键词逐一回归、大小写不敏感、短语关键词精确性。
 */
class RuleIntentClassifierBoundaryTest {

    private final RuleIntentClassifier classifier = new RuleIntentClassifier();

    @Test
    void nullInputDefaultsToChat() {
        assertEquals("chat", classifier.classify(null));
    }

    @Test
    void whitespaceOnlyDefaultsToChat() {
        assertEquals("chat", classifier.classify("   "));
        assertEquals("chat", classifier.classify("\n\t"));
    }

    @Test
    void codeTakesPriorityWhenBothCodeAndRagKeywordsPresent() {
        // 同时命中 code（代码）与 rag（检索）：code 关键词表先匹配
        assertEquals("code", classifier.classify("请根据检索结果写代码"));
        assertEquals("code", classifier.classify("rag 文档问答里嵌入 python 脚本"));
    }

    @Test
    void everyCodeKeywordRoutesToCode() {
        List<String> keywords = List.of("代码", "函数", "编程", "脚本", "重构", "编译",
                "bug", "debug", "java", "python", "sql", "json", "api实现", "单元测试");
        for (String kw : keywords) {
            assertEquals("code", classifier.classify("请帮我处理" + kw), "keyword: " + kw);
        }
    }

    @Test
    void everyRagKeywordRoutesToRag() {
        List<String> keywords = List.of("知识库", "检索", "文档问答", "rag", "根据资料", "根据文档");
        for (String kw : keywords) {
            assertEquals("rag", classifier.classify("请使用" + kw), "keyword: " + kw);
        }
    }

    @Test
    void keywordMatchingIsCaseInsensitive() {
        assertEquals("code", classifier.classify("请用 PyThOn 写"));
        assertEquals("code", classifier.classify("修复这个 BUG"));
        assertEquals("rag", classifier.classify("用 RAG 回答"));
    }

    @Test
    void apiImplKeywordRequiresExactPhrase() {
        // "api实现" 是整体短语：中间带空格不命中
        assertEquals("code", classifier.classify("api实现"));
        assertEquals("chat", classifier.classify("api 实现"));
    }

    @Test
    void substringOccurrencesCount() {
        // contains 语义：javascript 内含 "java"、debugger 内含 "debug" 也命中（已知边界行为）
        assertEquals("code", classifier.classify("javascript 问题"));
        assertEquals("code", classifier.classify("debugger 工具"));
    }

    @Test
    void pureChineseGeneralTextFallsBackToChat() {
        assertEquals("chat", classifier.classify("今天天气怎么样"));
        assertEquals("chat", classifier.classify("帮我写一首诗"));
    }

    @Test
    void singleKeywordAnywhereInTextMatches() {
        assertEquals("code", classifier.classify("sql"));
        assertEquals("rag", classifier.classify("知识库"));
    }
}

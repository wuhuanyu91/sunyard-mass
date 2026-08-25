package com.sunyard.llm.mas.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 规则意图分类测试（附录 G.7 L3）：code/rag 关键词命中，其余兜底 chat。
 */
class RuleIntentClassifierTest {

    private final RuleIntentClassifier classifier = new RuleIntentClassifier();

    @Test
    void codeKeywordsRouteToCode() {
        assertEquals("code", classifier.classify("帮我重构这段 Java 代码"));
        assertEquals("code", classifier.classify("这个函数有 bug，怎么 debug"));
        assertEquals("code", classifier.classify("写一个 SQL 查询"));
    }

    @Test
    void ragKeywordsRouteToRag() {
        assertEquals("rag", classifier.classify("根据文档回答这个问题"));
        assertEquals("rag", classifier.classify("从知识库里检索相关内容"));
    }

    @Test
    void generalTextFallsBackToChat() {
        assertEquals("chat", classifier.classify("今天天气怎么样"));
        assertEquals("chat", classifier.classify("北京是什么？"));
    }

    @Test
    void keywordMatchingIsCaseInsensitive() {
        assertEquals("code", classifier.classify("帮我看看这个 PYTHON 脚本"));
        assertEquals("rag", classifier.classify("用 RAG 技术回答"));
    }

    @Test
    void blankInputDefaultsToChat() {
        assertEquals("chat", classifier.classify(null));
        assertEquals("chat", classifier.classify(""));
        assertEquals("chat", classifier.classify("   "));
    }
}

package com.sunyard.llm.mas.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则难度评分边界测试：长度分档临界点（40/100/300）、关键词封顶、
 * 简单问句回降的启用边界、子任务/代码特征、分数截断。
 */
class RuleDifficultyClassifierBoundaryTest {

    private static final double EPS = 1e-9;
    private final RuleDifficultyClassifier classifier = new RuleDifficultyClassifier();

    // ---- 长度分档临界点 ----

    @Test
    void lengthBoundaryAt40() {
        assertEquals(0.0, classifier.score("x".repeat(40)), EPS);
        assertEquals(0.1, classifier.score("x".repeat(41)), EPS);
    }

    @Test
    void lengthBoundaryAt100() {
        assertEquals(0.1, classifier.score("x".repeat(100)), EPS);
        assertEquals(0.3, classifier.score("x".repeat(101)), EPS);
    }

    @Test
    void lengthBoundaryAt300() {
        assertEquals(0.3, classifier.score("x".repeat(300)), EPS);
        assertEquals(0.5, classifier.score("x".repeat(301)), EPS);
    }

    // ---- 关键词权重与封顶 ----

    @Test
    void singleComplexKeywordAddsPointTwo() {
        assertEquals(0.2, classifier.score("请证明一下"), EPS);
    }

    @Test
    void keywordScoreCapsAtPointSix() {
        assertEquals(0.6, classifier.score("证明推导定理"), EPS);
        // 第 4 个关键词不再加分
        assertEquals(0.6, classifier.score("证明推导定理算法"), EPS);
    }

    @Test
    void simpleKeywordDeductionOnlyWithin40Chars() {
        // 短文本：复杂词 0.4 - 简单词 0.2 = 0.2
        assertEquals(0.2, classifier.score("请证明推导这是什么"), EPS);
        // 长文本（41 字）：不回降 = 0.1 + 0.2
        String longText = "请证明这是什么" + "x".repeat(34);
        assertEquals(41, longText.length());
        assertEquals(0.3, classifier.score(longText), EPS);
    }

    @Test
    void simpleTextDeductsBelowZeroClampedToZero() {
        assertEquals(0.0, classifier.score("这是什么"), EPS);
    }

    // ---- 子任务结构特征 ----

    @Test
    void enumerationPatternsAddPointOneFive() {
        assertEquals(0.15, classifier.score("1. 子任务"), EPS);
        assertEquals(0.15, classifier.score("2、子任务"), EPS);
        assertEquals(0.15, classifier.score("①子任务"), EPS);
        assertEquals(0.15, classifier.score("- 子任务"), EPS);
    }

    @Test
    void multiLineTextAddsPointOneFive() {
        assertEquals(0.15, classifier.score("第一行\n第二行\n第三行"), EPS);
        // 两行不加分
        assertEquals(0.0, classifier.score("第一行\n第二行"), EPS);
    }

    // ---- 代码上下文特征 ----

    @Test
    void codeSignalsAddPointOneFive() {
        assertEquals(0.15, classifier.score("class Foo {}"), EPS);
        assertEquals(0.15, classifier.score("function f() {}"), EPS);
        assertEquals(0.15, classifier.score("代码块```\n内容"), EPS);
        // class 必须带空格（避免误伤 classic 等单词）
        assertEquals(0.0, classifier.score("classic music"), EPS);
    }

    // ---- 组合与截断 ----

    @Test
    void exactlyThresholdComposition() {
        // 长度 0.3（>100）+ 单关键词 0.2 = 恰好 0.5（阈值临界）
        String text = "x".repeat(101) + "证明";
        assertEquals(0.5, classifier.score(text), EPS);
    }

    @Test
    void heavyCompositionClampedToOne() {
        String heavy = ("证明推导定理算法架构优化重构 1. 任务\n").repeat(40) + "```class A{}```";
        double s = classifier.score(heavy);
        assertTrue(s <= 1.0 && s >= 0.5, "score out of band: " + s);
    }

    @Test
    void caseInsensitiveKeywordMatching() {
        // CLASS + 空格 触发代码特征（toLowerCase 后匹配）
        assertEquals(0.15, classifier.score("CLASS Foo {}"), EPS);
    }

    @Test
    void emptyAndWhitespaceOnlyScoreZero() {
        assertEquals(0.0, classifier.score(""), EPS);
        assertEquals(0.0, classifier.score("\n\t "), EPS);
    }

    @Test
    void nullInputScoresZero() {
        assertEquals(0.0, classifier.score(null), EPS);
    }
}

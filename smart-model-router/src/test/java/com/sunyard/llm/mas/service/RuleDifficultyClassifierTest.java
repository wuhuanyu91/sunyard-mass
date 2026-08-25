package com.sunyard.llm.mas.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则难度评分测试：简单问句低于阈值 0.5，复杂任务高于阈值，边界输入安全。
 */
class RuleDifficultyClassifierTest {

    private final RuleDifficultyClassifier classifier = new RuleDifficultyClassifier();

    @Test
    void shortSimpleQuestionScoresLow() {
        assertTrue(classifier.score("北京是什么？") < 0.5);
        assertTrue(classifier.score("现在几点了") < 0.5);
    }

    @Test
    void complexTaskScoresHigh() {
        String complex = "请设计一个分布式系统的架构方案，对比分析三种一致性协议的权衡，"
                + "逐步推导可用性指标的计算原理，并给出多步骤优化的详细评估";
        assertTrue(classifier.score(complex) >= 0.5);
    }

    @Test
    void longTextAndCodeRaiseScore() {
        String longText = "背景资料".repeat(120) + "\n```\nfunction f() {}\n```";
        assertTrue(classifier.score(longText) >= 0.5);
    }

    @Test
    void blankInputScoresZero() {
        assertEquals(0.0, classifier.score(null));
        assertEquals(0.0, classifier.score("  "));
    }

    @Test
    void scoreIsClampedToUnitInterval() {
        String heavy = "证明并推导定理原理算法架构设计方案，".repeat(20)
                + "\n1. 子任务一\n2. 子任务二\n3. 子任务三\n```\nclass A {}\n```";
        double score = classifier.score(heavy);
        assertTrue(score >= 0.0 && score <= 1.0);
    }
}

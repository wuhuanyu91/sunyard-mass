package com.sunyard.llm.mas.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 规则难度评分（DifficultyClassifier 默认实现）：
 * 文本长度、复杂任务信号词、多子任务结构、代码上下文四类特征加权求和后截断到 [0,1]。
 * 规则与权重可通过后续校准（mas_call_log 的 intent/routed_to 统计）迭代。
 */
@Component
public class RuleDifficultyClassifier implements DifficultyClassifier {

    /** 复杂任务信号词：出现越多越倾向大模型 */
    private static final List<String> COMPLEX_KEYWORDS = List.of(
            "证明", "推导", "设计方案", "架构", "对比分析", "评估", "权衡",
            "优化", "重构", "原理", "定理", "算法", "规划", "多步骤", "逐步",
            "深入分析", "详细分析", "全面", "实现一个", "设计一个", "写一篇");

    /** 简单问答信号词：命中且文本短则倾向小模型 */
    private static final List<String> SIMPLE_KEYWORDS = List.of(
            "是什么", "什么是", "多少钱", "几点", "翻译", "总结一下", "是谁", "在哪");

    @Override
    public double score(String userText) {
        if (userText == null || userText.isBlank()) {
            return 0.0;
        }
        String lower = userText.toLowerCase(Locale.ROOT);
        double score = 0.0;

        // 1) 长度信号：短问句通常简单，长文本（含资料/上下文）通常复杂
        int len = userText.length();
        if (len > 300) {
            score += 0.5;
        } else if (len > 100) {
            score += 0.3;
        } else if (len > 40) {
            score += 0.1;
        }

        // 2) 复杂信号词：每命中一个 +0.2，封顶 0.6
        double kwScore = 0.0;
        for (String kw : COMPLEX_KEYWORDS) {
            if (lower.contains(kw)) {
                kwScore += 0.2;
            }
        }
        score += Math.min(kwScore, 0.6);

        // 3) 多子任务结构：枚举/多行通常意味着复合任务
        int lines = userText.split("\n").length;
        boolean enumerated = lower.matches("(?s).*([0-9]+[.、)]|①|②|- ).*");
        if (lines >= 3 || enumerated) {
            score += 0.15;
        }

        // 4) 代码上下文：携带代码块的问题通常需要更强推理
        if (userText.contains("```") || lower.contains("function ") || lower.contains("class ")) {
            score += 0.15;
        }

        // 5) 简单问答信号：短文本 + 简单问句回降
        if (len <= 40) {
            for (String kw : SIMPLE_KEYWORDS) {
                if (lower.contains(kw)) {
                    score -= 0.2;
                    break;
                }
            }
        }

        return Math.max(0.0, Math.min(1.0, score));
    }
}

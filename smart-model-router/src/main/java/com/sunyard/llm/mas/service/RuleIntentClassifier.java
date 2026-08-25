package com.sunyard.llm.mas.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 规则/关键词意图分类（IntentClassifier SPI 默认实现，附录 G.7 L3）。
 */
@Component
public class RuleIntentClassifier implements IntentClassifier {

    private static final List<String> CODE_KEYWORDS = List.of(
            "代码", "函数", "编程", "脚本", "重构", "编译", "bug", "debug",
            "java", "python", "sql", "json", "api实现", "单元测试");

    private static final List<String> RAG_KEYWORDS = List.of(
            "知识库", "检索", "文档问答", "rag", "根据资料", "根据文档");

    @Override
    public String classify(String userText) {
        if (userText == null || userText.isBlank()) {
            return "chat";
        }
        String lower = userText.toLowerCase(Locale.ROOT);
        for (String kw : CODE_KEYWORDS) {
            if (lower.contains(kw)) {
                return "code";
            }
        }
        for (String kw : RAG_KEYWORDS) {
            if (lower.contains(kw)) {
                return "rag";
            }
        }
        return "chat";
    }
}

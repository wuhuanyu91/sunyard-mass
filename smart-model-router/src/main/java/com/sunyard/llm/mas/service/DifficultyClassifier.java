package com.sunyard.llm.mas.service;

/**
 * 难度评分 SPI：对用户问题输出 0-1 难度分，L3 据此在 simple/complex 模型间路由。
 * 原型为规则评分实现；生产阶段可替换为小参数分类模型（DJL/ONNX）或 LLM 裁判实现，
 * 与 IntentClassifier SPI 同构演进（附录 E.1）。
 */
public interface DifficultyClassifier {

    /** @return [0,1] 难度分，>= mas.routing.difficulty.threshold 判定为 complex */
    double score(String userText);
}

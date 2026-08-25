package com.sunyard.llm.mas.service;

/**
 * 意图分类 SPI（附录 E.1）：原型为规则/关键词实现，
 * 生产阶段替换为 DJL/ONNX 0.5B 分类模型加载实现。
 */
public interface IntentClassifier {

    /** @return chat / code / rag / analysis / multimodal / embedding / reasoning 之一 */
    String classify(String userText);
}

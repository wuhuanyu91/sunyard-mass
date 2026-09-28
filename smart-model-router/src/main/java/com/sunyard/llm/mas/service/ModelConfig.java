package com.sunyard.llm.mas.service;

/**
 * mas_model_config 行的内存视图。
 */
public record ModelConfig(
        String modelId,
        String modelName,
        String provider,
        String endpointUrl,
        String intentType,
        int weight,
        int status,
        Integer maxContextTokens,
        /** 部署形态：LOCAL 本地自建 / CLOUD 云端 / RENTAL 外部租赁（数据分级管控依据） */
        String deployType) {

    public boolean active() {
        return status == 1;
    }

    /** 部署形态，缺省按 LOCAL 处理（最严格的可用形态） */
    public String deployTypeOrDefault() {
        return deployType == null || deployType.isBlank() ? "LOCAL" : deployType.trim().toUpperCase();
    }

    /** 无任何模型配置时的兜底（mas.backend.default-endpoint） */
    public static ModelConfig fallback(String defaultModel, String defaultEndpoint) {
        return new ModelConfig(defaultModel, defaultModel, "fallback", defaultEndpoint, "chat", 100, 1, null, "LOCAL");
    }
}

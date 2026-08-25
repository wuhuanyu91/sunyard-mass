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
        Integer maxContextTokens) {

    public boolean active() {
        return status == 1;
    }

    /** 无任何模型配置时的兜底（mas.backend.default-endpoint） */
    public static ModelConfig fallback(String defaultModel, String defaultEndpoint) {
        return new ModelConfig(defaultModel, defaultModel, "fallback", defaultEndpoint, "chat", 100, 1, null);
    }
}

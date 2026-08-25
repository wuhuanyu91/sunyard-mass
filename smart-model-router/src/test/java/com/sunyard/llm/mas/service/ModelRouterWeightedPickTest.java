package com.sunyard.llm.mas.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 加权选择与模型配置视图测试（§9.2 同意图多实例 weight 加权）。
 */
class ModelRouterWeightedPickTest {

    private final ModelRouter router = new ModelRouter(null, null);

    private static ModelConfig model(String id, int weight) {
        return new ModelConfig(id, id, "ollama", "http://localhost:11434/v1", "chat", weight, 1, null);
    }

    @Test
    void emptyCandidatesReturnNull() {
        assertNull(router.weightedPick(List.of()));
    }

    @Test
    void singleCandidateAlwaysPicked() {
        ModelConfig only = model("solo", 1);
        for (int i = 0; i < 20; i++) {
            assertEquals("solo", router.weightedPick(List.of(only)).modelId());
        }
    }

    @Test
    void weightDistributionApproximatesRatio() {
        ModelConfig heavy = model("heavy", 90);
        ModelConfig light = model("light", 10);
        Map<String, Integer> hits = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            ModelConfig picked = router.weightedPick(List.of(heavy, light));
            hits.merge(picked.modelId(), 1, Integer::sum);
        }
        // 90:10 权重，允许统计波动：heavy 占比应落在 85%~95%
        double heavyRatio = hits.getOrDefault("heavy", 0) / 10_000.0;
        assertTrue(heavyRatio > 0.85 && heavyRatio < 0.95,
                "heavy ratio out of expected band: " + heavyRatio);
        assertNotNull(hits.get("light"));
    }

    @Test
    void zeroWeightStillEligibleWithFloorOne() {
        // weight<=0 按 1 保底，不应被完全排除
        ModelConfig zero = model("zero", 0);
        ModelConfig normal = model("normal", 100);
        int zeroHits = 0;
        for (int i = 0; i < 2_000; i++) {
            if ("zero".equals(router.weightedPick(List.of(zero, normal)).modelId())) {
                zeroHits++;
            }
        }
        assertTrue(zeroHits > 0);
    }

    @Test
    void modelConfigActiveAndFallback() {
        assertTrue(model("a", 10).active());
        assertFalse(new ModelConfig("b", "b", "ollama", "http://x", "chat", 10, 0, null).active());
        ModelConfig fb = ModelConfig.fallback("qwen-72b", "http://localhost:11434/v1");
        assertEquals("qwen-72b", fb.modelId());
        assertEquals("fallback", fb.provider());
        assertTrue(fb.active());
    }
}

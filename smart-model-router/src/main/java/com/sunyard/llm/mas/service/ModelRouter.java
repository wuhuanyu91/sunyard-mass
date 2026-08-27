package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.entity.ModelConfigEntity;
import com.sunyard.llm.mas.mapper.ModelConfigMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型路由注册表（§9.2 ModelRouter）：
 * 从 mas_model_config 加载内存快照，30 秒刷新（管理面接入后即为热加载点，附录 H.3）。
 * 同意图多实例按 weight 加权选择；Resilience4j 熔断故障转移为生产增强项。
 */
@Service
public class ModelRouter {

    private static final Logger log = LoggerFactory.getLogger(ModelRouter.class);

    private final ModelConfigMapper mapper;
    private final MasProperties props;

    private volatile Map<String, ModelConfig> byId = Map.of();
    private volatile List<ModelConfig> active = List.of();

    public ModelRouter(ModelConfigMapper mapper, MasProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    @PostConstruct
    public void init() {
        refresh().subscribe(null, e -> log.warn("Model config initial load failed (fallback only): {}", e.getMessage()));
    }

    @Scheduled(fixedDelay = 30_000)
    public void scheduledRefresh() {
        refresh().subscribe(null, e -> log.warn("Model config refresh failed: {}", e.getMessage()));
    }

    public Mono<Void> refresh() {
        return ReactiveDbAdapter.mono(mapper::selectAll)
                .map(list -> list.stream().map(this::toModelConfig).toList())
                .doOnNext(list -> {
                    this.byId = list.stream().collect(Collectors.toMap(ModelConfig::modelId, Function.identity(), (a, b) -> a));
                    this.active = list.stream().filter(ModelConfig::active).toList();
                    log.debug("Model config reloaded, {} models ({} active)", list.size(), active.size());
                })
                .then();
    }

    private ModelConfig toModelConfig(ModelConfigEntity e) {
        return new ModelConfig(
                e.getModelId(),
                e.getModelName(),
                e.getProvider(),
                e.getEndpointUrl(),
                e.getIntentType(),
                e.getWeight() != null ? e.getWeight() : 0,
                e.getStatus() != null ? e.getStatus() : 0,
                e.getMaxContextTokens());
    }

    /** 按 model_id 精确查找（含未启用，由调用方判断状态） */
    public ModelConfig getModel(String modelId) {
        return byId.get(modelId);
    }

    public List<ModelConfig> activeModels() {
        return active;
    }

    /** 按意图加权随机选择启用中的模型 */
    public ModelConfig pickByIntent(String intent) {
        List<ModelConfig> candidates = active.stream()
                .filter(c -> c.intentType().equalsIgnoreCase(intent))
                .toList();
        return weightedPick(candidates);
    }

    /** 兜底模型（mas.routing.default-model） */
    public ModelConfig defaultModel() {
        ModelConfig cfg = byId.get(props.getRouting().getDefaultModel());
        if (cfg != null && cfg.active()) {
            return cfg;
        }
        return ModelConfig.fallback(props.getRouting().getDefaultModel(), props.getBackend().getDefaultEndpoint());
    }

    /** 包级可见供单测直接验证加权分布（生产调用方仅 pickByIntent） */
    ModelConfig weightedPick(List<ModelConfig> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        int totalWeight = candidates.stream().mapToInt(c -> Math.max(1, c.weight())).sum();
        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        for (ModelConfig c : candidates) {
            r -= Math.max(1, c.weight());
            if (r < 0) {
                return c;
            }
        }
        return candidates.get(candidates.size() - 1);
    }
}

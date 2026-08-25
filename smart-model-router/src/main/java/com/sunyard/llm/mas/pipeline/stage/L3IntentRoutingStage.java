package com.sunyard.llm.mas.pipeline.stage;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.service.DifficultyClassifier;
import com.sunyard.llm.mas.service.IntentClassifier;
import com.sunyard.llm.mas.service.ModelConfig;
import com.sunyard.llm.mas.service.ModelRouter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * L3 意图路由层（§5.3 / 附录 E.1/G.7）：
 * 显式 model 字段优先 → 难度评分路由（simple/complex 模型分流，默认开启）→ 关键词规则推断意图 → 默认模型。
 * 请求的模型未注册时返回 404 model_not_found（附录 G.2）。
 */
@Component
public class L3IntentRoutingStage {

    private final ModelRouter modelRouter;
    private final IntentClassifier intentClassifier;
    private final DifficultyClassifier difficultyClassifier;
    private final MasProperties props;

    public L3IntentRoutingStage(ModelRouter modelRouter, IntentClassifier intentClassifier,
                                DifficultyClassifier difficultyClassifier, MasProperties props) {
        this.modelRouter = modelRouter;
        this.intentClassifier = intentClassifier;
        this.difficultyClassifier = difficultyClassifier;
        this.props = props;
    }

    public Mono<Void> route(PipelineContext ctx) {
        return Mono.fromRunnable(() -> {
            String model = ctx.getRequestedModel();
            if (model != null && !model.isBlank()) {
                ModelConfig cfg = modelRouter.getModel(model);
                if (cfg == null || !cfg.active()) {
                    throw MasException.modelNotFound(model);
                }
                ctx.setTarget(cfg);
                ctx.setIntent(cfg.intentType());
            } else {
                String text = L2MultiLevelCacheStage.lastUserText(ctx.getRequest());
                ModelConfig cfg = routeByDifficulty(ctx, text);
                if (cfg == null) {
                    // 难度路由未命中（关闭或未注册 simple/complex 模型）：回退意图路由
                    String intent = intentClassifier.classify(text);
                    ctx.setIntent(intent);
                    cfg = modelRouter.pickByIntent(intent);
                    if (cfg == null) {
                        cfg = modelRouter.defaultModel();
                    }
                }
                ctx.setTarget(cfg);
            }
            ctx.getMeta().setIntent(ctx.getIntent());
            ctx.getMeta().setRoutedTo(ctx.getTarget().endpointUrl());
        }).then();
    }

    /**
     * 难度路由：评分 >= 阈值 → complex（大参数模型），否则 simple（小参数模型）。
     * 返回 null 表示不适用（开关关闭或注册表无对应档位模型），由调用方回退。
     */
    private ModelConfig routeByDifficulty(PipelineContext ctx, String text) {
        MasProperties.Difficulty difficulty = props.getRouting().getDifficulty();
        if (!difficulty.isEnabled()) {
            return null;
        }
        double score = difficultyClassifier.score(text);
        String level = score >= difficulty.getThreshold() ? "complex" : "simple";
        ModelConfig cfg = modelRouter.pickByIntent(level);
        if (cfg == null) {
            return null;
        }
        ctx.setIntent(level);
        ctx.getMeta().setDifficulty(score);
        return cfg;
    }
}

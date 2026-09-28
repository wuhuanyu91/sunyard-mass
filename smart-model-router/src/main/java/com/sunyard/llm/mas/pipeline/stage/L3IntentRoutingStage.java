package com.sunyard.llm.mas.pipeline.stage;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.service.DataLevelGuardService;
import com.sunyard.llm.mas.service.DifficultyClassifier;
import com.sunyard.llm.mas.service.GrayReleaseService;
import com.sunyard.llm.mas.service.IntentClassifier;
import com.sunyard.llm.mas.service.ModelConfig;
import com.sunyard.llm.mas.service.ModelRouter;
import com.sunyard.llm.mas.service.PolicyRuntimeService;
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
    private final DataLevelGuardService dataLevelGuard;
    private final PolicyRuntimeService policyRuntime;
    private final GrayReleaseService grayRelease;
    private final MasProperties props;

    public L3IntentRoutingStage(ModelRouter modelRouter, IntentClassifier intentClassifier,
                                DifficultyClassifier difficultyClassifier,
                                DataLevelGuardService dataLevelGuard,
                                PolicyRuntimeService policyRuntime,
                                GrayReleaseService grayRelease,
                                MasProperties props) {
        this.modelRouter = modelRouter;
        this.intentClassifier = intentClassifier;
        this.difficultyClassifier = difficultyClassifier;
        this.dataLevelGuard = dataLevelGuard;
        this.policyRuntime = policyRuntime;
        this.grayRelease = grayRelease;
        this.props = props;
    }

    public Mono<Void> route(PipelineContext ctx) {
        // 统一控制面：路由类策略在选模型之前生效，限定候选集
        PolicyRuntimeService.RoutingConstraint constraint = policyRuntime.evaluateL3(ctx);
        return Mono.fromRunnable(() -> {
            String model = ctx.getRequestedModel();
            if (model != null && !model.isBlank()) {
                if (!constraint.allows(model)) {
                    // 显式指定的模型不在策略允许清单内：改选清单里的第一个可用模型
                    ModelConfig replacement = firstAllowed(constraint);
                    if (replacement == null) {
                        throw MasException.modelNotFound(model + "（已被路由策略限制）");
                    }
                    model = replacement.modelId();
                }
                ModelConfig cfg = modelRouter.getModel(model);
                if (cfg == null || !cfg.active()) {
                    throw MasException.modelNotFound(model);
                }
                // 数据分级差异化管控：显式指定模型同样要过合规闸口，
                // L3 敏感数据不得被路由到云端/租赁算力承载的模型上
                cfg = dataLevelGuard.guard(ctx.getAppId(), cfg, cfg.intentType());
                // 灰度发布：命中进行中的发布单则切到目标版本（回滚/中止立即失效）
                cfg = applyGrayRelease(ctx, cfg, cfg.intentType());
                ctx.setTarget(cfg);
                ctx.setIntent(cfg.intentType());
            } else {
                String text = L2MultiLevelCacheStage.lastUserText(ctx.getRequest());
                // 命中限流降级动作：优先走小模型档位，用能力换可用性
                ModelConfig cfg = ctx.isDowngraded() ? modelRouter.pickByIntent("simple") : null;
                if (cfg == null) {
                    cfg = routeByDifficulty(ctx, text);
                }
                if (cfg == null) {
                    // 难度路由未命中（关闭或未注册 simple/complex 模型）：回退意图路由
                    String intent = intentClassifier.classify(text);
                    ctx.setIntent(intent);
                    cfg = modelRouter.pickByIntent(intent);
                    if (cfg == null) {
                        cfg = modelRouter.defaultModel();
                    }
                }
                // 自动路由结果同样要过数据分级闸口（不合规则改选合规模型）
                cfg = dataLevelGuard.guard(ctx.getAppId(), cfg, ctx.getIntent());
                // 路由策略限定的模型清单优先于自动路由结果
                if (!constraint.allows(cfg.modelId())) {
                    ModelConfig replacement = firstAllowed(constraint);
                    if (replacement != null) cfg = replacement;
                }
                cfg = applyGrayRelease(ctx, cfg, ctx.getIntent());
                ctx.setTarget(cfg);
            }
            ctx.getMeta().setIntent(ctx.getIntent());
            ctx.getMeta().setRoutedTo(ctx.getTarget().endpointUrl());
        }).then();
    }

    /**
     * 灰度发布接入（POC 第 9 问）：命中发布单则改选目标版本对应的已注册模型。
     * 目标版本解析顺序：① 版本号本身就是一个已注册模型 id；② modelId@version 约定命名。
     * 两者都解析不到时保持原模型（fail-safe，绝不因为配置缺失打断调用）。
     */
    private ModelConfig applyGrayRelease(PipelineContext ctx, ModelConfig cfg, String intent) {
        if (grayRelease == null || cfg == null) return cfg;
        GrayReleaseService.Release rel = grayRelease.of(cfg.modelId());
        if (rel == null) return cfg;
        if (!grayRelease.hit(rel, ctx.getUserId(), ctx.getAppId(), ctx.getTraceId())) return cfg;
        String targetId = resolveGrayModel(cfg.modelId(), rel.toVersion());
        if (targetId == null) return cfg;
        ModelConfig grayCfg = modelRouter.getModel(targetId);
        if (grayCfg == null || !grayCfg.active()) return cfg;
        // 灰度目标同样要过数据分级闸口（新版本部署形态可能与旧版本不同）
        grayCfg = dataLevelGuard.guard(ctx.getAppId(), grayCfg, intent == null ? grayCfg.intentType() : intent);
        ctx.setGrayReleaseId(rel.releaseId());
        ctx.getMeta().setGrayRelease(rel.releaseId());
        return grayCfg;
    }

    private String resolveGrayModel(String modelId, String toVersion) {
        if (toVersion == null || toVersion.isBlank()) return null;
        if (modelRouter.getModel(toVersion) != null) return toVersion;
        String composed = modelId + "@" + toVersion;
        if (modelRouter.getModel(composed) != null) return composed;
        return null;
    }

    /** 从策略允许清单里取第一个当前可用的模型 */
    private ModelConfig firstAllowed(PolicyRuntimeService.RoutingConstraint constraint) {
        for (String id : constraint.allowedModels()) {
            ModelConfig c = modelRouter.getModel(id);
            if (c != null && c.active()) return c;
        }
        return null;
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

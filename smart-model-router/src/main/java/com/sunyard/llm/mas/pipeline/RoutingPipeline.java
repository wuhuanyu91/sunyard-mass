package com.sunyard.llm.mas.pipeline;

import com.sunyard.llm.mas.pipeline.stage.L1RuleInterceptStage;
import com.sunyard.llm.mas.pipeline.stage.L2MultiLevelCacheStage;
import com.sunyard.llm.mas.pipeline.stage.L3IntentRoutingStage;
import com.sunyard.llm.mas.pipeline.stage.L4ExecutionControlStage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 流水线编排器（§2.2 请求处理流程）：
 * L1 规则拦截 → L2 多级缓存（命中即短路）→ L3 意图路由 → L4 执行管控。
 */
@Component
public class RoutingPipeline {

    private final L1RuleInterceptStage l1;
    private final L2MultiLevelCacheStage l2;
    private final L3IntentRoutingStage l3;
    private final L4ExecutionControlStage l4;

    public RoutingPipeline(L1RuleInterceptStage l1,
                           L2MultiLevelCacheStage l2,
                           L3IntentRoutingStage l3,
                           L4ExecutionControlStage l4) {
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.l4 = l4;
    }

    public Mono<Void> process(PipelineContext ctx) {
        return l1.check(ctx)
                .then(Mono.defer(() -> l2.lookup(ctx)))
                .then(Mono.defer(() -> {
                    if (ctx.getCachedResponse() != null) {
                        // 缓存命中：跳过 L3/L4 直接返回（§2.2）
                        return Mono.empty();
                    }
                    return l3.route(ctx).then(Mono.defer(() -> l4.control(ctx)));
                }));
    }
}

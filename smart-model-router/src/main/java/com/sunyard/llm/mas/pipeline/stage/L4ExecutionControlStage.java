package com.sunyard.llm.mas.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.service.DeptQuotaService;
import com.sunyard.llm.mas.service.QuotaService;
import com.sunyard.llm.mas.service.TokenCounter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * L4 执行管控层（请求阶段，§5.4 / 附录 G.7）：
 * 上下文截断压缩（保留全部 system + 最近轮次）→ Token 配额原子预扣。
 * 输出审核在响应阶段由 ForwardService 执行。
 */
@Component
public class L4ExecutionControlStage {

    private final QuotaService quotaService;
    private final DeptQuotaService deptQuotaService;
    private final MasProperties props;

    public L4ExecutionControlStage(QuotaService quotaService, DeptQuotaService deptQuotaService,
                                   MasProperties props) {
        this.quotaService = quotaService;
        this.deptQuotaService = deptQuotaService;
        this.props = props;
    }

    public Mono<Void> control(PipelineContext ctx) {
        compress(ctx);
        int promptTokens = TokenCounter.countMessages(ctx.getRequest().path("messages"));
        ctx.setPromptTokens(promptTokens);
        int maxTokens = ctx.getRequest().path("max_tokens").isInt()
                ? ctx.getRequest().path("max_tokens").asInt()
                : 1024;
        int reserved = promptTokens + maxTokens;
        // 部门/租户级配额闸口（over_limit_stop 在此真正生效），随后才是用户级配额预扣
        return deptQuotaService.check(ctx.getAppId(), reserved)
                .then(quotaService.reserve(ctx.getUserId(), reserved))
                .doOnSuccess(v -> ctx.setReservedTokens(reserved));
    }

    /** 截断压缩：token 超限时从最早的非 system 消息开始丢弃 */
    private void compress(PipelineContext ctx) {
        int limit = props.getCompress().getMaxContextTokens();
        JsonNode messages = ctx.getRequest().path("messages");
        if (!messages.isArray() || TokenCounter.countMessages(messages) <= limit) {
            return;
        }
        ArrayNode kept = ctx.getRequest().arrayNode();
        // 保留全部 system message
        for (JsonNode m : messages) {
            if ("system".equals(m.path("role").asText())) {
                kept.add(m);
            }
        }
        // 从后向前保留非 system 消息直到逼近上限
        ArrayNode tail = ctx.getRequest().arrayNode();
        int tokens = TokenCounter.countMessages(kept);
        for (int i = messages.size() - 1; i >= 0; i--) {
            JsonNode m = messages.get(i);
            if ("system".equals(m.path("role").asText())) {
                continue;
            }
            int cost = TokenCounter.countMessages(ctx.getRequest().arrayNode().add(m));
            if (tokens + cost > limit && !tail.isEmpty()) {
                break;
            }
            tail.insert(0, m);
            tokens += cost;
        }
        kept.addAll(tail);
        ((ObjectNode) ctx.getRequest()).set("messages", kept);
    }
}

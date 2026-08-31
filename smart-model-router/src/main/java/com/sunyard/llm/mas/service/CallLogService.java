package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.mapper.CallLogMapper;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 调用记录写入 mas_call_log（fire-and-forget，失败仅告警）。
 */
@Service
public class CallLogService {

    private static final Logger log = LoggerFactory.getLogger(CallLogService.class);

    private final CallLogMapper mapper;

    public CallLogService(CallLogMapper mapper) {
        this.mapper = mapper;
    }

    public void logAsync(PipelineContext ctx, int promptTokens, int completionTokens,
                         int totalTokens, long totalCostMs, boolean success) {
        ReactiveDbAdapter.monoVoid(() -> mapper.insertCallLog(
                        ctx.getTraceId() == null ? "" : ctx.getTraceId(),
                        emptyIfNull(ctx.getAppId()),
                        ctx.getUserId(),
                        emptyIfNull(ctx.getAgentId()),
                        emptyIfNull(ctx.getRequestedModel()),
                        emptyIfNull(ctx.getIntent()),
                        ctx.getMeta().isCacheHit() ? 1 : 0,
                        emptyIfNull(ctx.getMeta().getCacheLevel()),
                        emptyIfNull(ctx.getMeta().getRoutedTo()),
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        (int) ctx.getMeta().getPipelineCostMs(),
                        (int) totalCostMs,
                        success ? 0 : 1))
                .subscribe(null, e -> log.warn("Call log write failed: {}", e.getMessage()));
    }

    /** MyBatis 不接受 null，统一以空串占位 */
    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}

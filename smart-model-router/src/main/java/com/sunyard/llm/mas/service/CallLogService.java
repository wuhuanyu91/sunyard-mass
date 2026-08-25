package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.pipeline.PipelineContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;

/**
 * 调用记录写入 mas_call_log（fire-and-forget，失败仅告警）。
 */
@Service
public class CallLogService {

    private static final Logger log = LoggerFactory.getLogger(CallLogService.class);

    private final DatabaseClient db;

    public CallLogService(DatabaseClient db) {
        this.db = db;
    }

    public void logAsync(PipelineContext ctx, int promptTokens, int completionTokens,
                         int totalTokens, long totalCostMs, boolean success) {
        db.sql("""
                        INSERT INTO mas_call_log (trace_id, app_id, user_id, model_id, intent_type,
                            cache_hit, cache_level, routed_to, prompt_tokens, completion_tokens,
                            total_tokens, pipeline_cost_ms, total_cost_ms, status)
                        VALUES (:trace, :app, :user, :model, :intent,
                            :hit, :level, :routed, :pt, :ct, :tt, :pc, :tc, :status)
                        """)
                .bind("trace", ctx.getTraceId() == null ? "" : ctx.getTraceId())
                .bind("app", emptyIfNull(ctx.getAppId()))
                .bind("user", ctx.getUserId())
                .bind("model", emptyIfNull(ctx.getRequestedModel()))
                .bind("intent", emptyIfNull(ctx.getIntent()))
                .bind("hit", (short) (ctx.getMeta().isCacheHit() ? 1 : 0))
                .bind("level", emptyIfNull(ctx.getMeta().getCacheLevel()))
                .bind("routed", emptyIfNull(ctx.getMeta().getRoutedTo()))
                .bind("pt", promptTokens)
                .bind("ct", completionTokens)
                .bind("tt", totalTokens)
                .bind("pc", (int) ctx.getMeta().getPipelineCostMs())
                .bind("tc", (int) totalCostMs)
                .bind("status", (short) (success ? 0 : 1))
                .then()
                .subscribe(null, e -> log.warn("Call log write failed: {}", e.getMessage()));
    }

    /** R2DBC bind 不接受 null，统一以空串占位 */
    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }
}

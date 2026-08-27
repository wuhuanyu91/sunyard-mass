package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.CallLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * mas_call_log 调用记录 Mapper
 */
@Mapper
public interface CallLogMapper extends BaseMapper<CallLogEntity> {

    @Insert("""
            INSERT INTO mas_call_log (trace_id, app_id, user_id, model_id, intent_type,
                cache_hit, cache_level, routed_to, prompt_tokens, completion_tokens,
                total_tokens, pipeline_cost_ms, total_cost_ms, status)
            VALUES (#{traceId}, #{appId}, #{userId}, #{modelId}, #{intentType},
                #{cacheHit}, #{cacheLevel}, #{routedTo}, #{promptTokens}, #{completionTokens},
                #{totalTokens}, #{pipelineCostMs}, #{totalCostMs}, #{status})
            """)
    int insertCallLog(@Param("traceId") String traceId,
                      @Param("appId") String appId,
                      @Param("userId") String userId,
                      @Param("modelId") String modelId,
                      @Param("intentType") String intentType,
                      @Param("cacheHit") Integer cacheHit,
                      @Param("cacheLevel") String cacheLevel,
                      @Param("routedTo") String routedTo,
                      @Param("promptTokens") Integer promptTokens,
                      @Param("completionTokens") Integer completionTokens,
                      @Param("totalTokens") Integer totalTokens,
                      @Param("pipelineCostMs") Integer pipelineCostMs,
                      @Param("totalCostMs") Integer totalCostMs,
                      @Param("status") Integer status);
}

package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.CallLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Map;

/**
 * mas_call_log 调用记录 Mapper
 */
@Mapper
public interface CallLogMapper extends BaseMapper<CallLogEntity> {

    @Insert("""
            INSERT INTO mas_call_log (trace_id, app_id, user_id, agent_id, model_id, intent_type,
                cache_hit, cache_level, routed_to, prompt_tokens, completion_tokens,
                total_tokens, pipeline_cost_ms, total_cost_ms, status, tenant_id)
            VALUES (#{traceId}, #{appId}, #{userId}, #{agentId}, #{modelId}, #{intentType},
                #{cacheHit}, #{cacheLevel}, #{routedTo}, #{promptTokens}, #{completionTokens},
                #{totalTokens}, #{pipelineCostMs}, #{totalCostMs}, #{status}, #{tenantId})
            """)
    int insertCallLog(@Param("traceId") String traceId,
                      @Param("appId") String appId,
                      @Param("userId") String userId,
                      @Param("agentId") String agentId,
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
                      @Param("status") Integer status,
                      @Param("tenantId") String tenantId);

    /**
     * 全字段写入（公告二-8 审计追溯 + 一-5 差异化计量维度）：
     * 增加请求/响应内容留存、内容防篡改哈希、部门/场景/服务类型、单笔成本与账期。
     */
    @Insert("""
            INSERT INTO mas_call_log (trace_id, app_id, user_id, agent_id, model_id, intent_type,
                cache_hit, cache_level, routed_to, prompt_tokens, completion_tokens,
                total_tokens, pipeline_cost_ms, total_cost_ms, status, tenant_id,
                dept_id, sla_level, data_level, scenario, service_type,
                request_content, response_content, content_hash, cost_amount, bill_month)
            VALUES (#{traceId}, #{appId}, #{userId}, #{agentId}, #{modelId}, #{intentType},
                #{cacheHit}, #{cacheLevel}, #{routedTo}, #{promptTokens}, #{completionTokens},
                #{totalTokens}, #{pipelineCostMs}, #{totalCostMs}, #{status}, #{tenantId},
                #{deptId}, #{slaLevel}, #{dataLevel}, #{scenario}, #{serviceType},
                #{requestContent}, #{responseContent}, #{contentHash}, #{costAmount}, #{billMonth})
            """)
    int insertCallLogFull(@Param("traceId") String traceId,
                          @Param("appId") String appId,
                          @Param("userId") String userId,
                          @Param("agentId") String agentId,
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
                          @Param("status") Integer status,
                          @Param("tenantId") String tenantId,
                          @Param("deptId") String deptId,
                          @Param("slaLevel") String slaLevel,
                          @Param("dataLevel") String dataLevel,
                          @Param("scenario") String scenario,
                          @Param("serviceType") String serviceType,
                          @Param("requestContent") String requestContent,
                          @Param("responseContent") String responseContent,
                          @Param("contentHash") String contentHash,
                          @Param("costAmount") java.math.BigDecimal costAmount,
                          @Param("billMonth") String billMonth);

    /** 响应完成后回填响应内容并重算防篡改哈希（审计留存） */
    @Update("UPDATE mas_call_log SET response_content = #{responseContent}, " +
            "content_hash = #{contentHash} WHERE trace_id = #{traceId}")
    int updateResponseContent(@Param("traceId") String traceId,
                              @Param("responseContent") String responseContent,
                              @Param("contentHash") String contentHash);

    @Select("SELECT trace_id, request_content, response_content, content_hash FROM mas_call_log " +
            "WHERE trace_id = #{traceId}")
    Map<String, Object> selectContent(@Param("traceId") String traceId);
}

package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.SecurityEventEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 安全事件与检测规则（公告二-7 行为监测、异常识别）：
 * 此前 mas_security_event 全项目无 INSERT，数据 100% 来自迁移脚本种子；
 * 现补齐检测规则表与实时检测写入链路。
 */
@Mapper
public interface SecurityEventMapper extends BaseMapper<SecurityEventEntity> {

    // ---------------- 检测规则 ----------------

    @Select("SELECT id, rule_code, rule_name, event_type, severity, condition_json, action, status, created_at " +
            "FROM mas_security_rule ORDER BY id")
    List<Map<String, Object>> listRules();

    @Insert("INSERT INTO mas_security_rule (rule_code, rule_name, event_type, severity, condition_json, action, status) " +
            "VALUES (#{ruleCode}, #{ruleName}, #{eventType}, #{severity}, #{conditionJson}, " +
            "COALESCE(#{action},'ALERT'), COALESCE(#{status},1)) ON CONFLICT (rule_code) DO NOTHING")
    int insertRule(@Param("ruleCode") String ruleCode,
                   @Param("ruleName") String ruleName,
                   @Param("eventType") String eventType,
                   @Param("severity") String severity,
                   @Param("conditionJson") String conditionJson,
                   @Param("action") String action,
                   @Param("status") Integer status);

    @Update("UPDATE mas_security_rule SET rule_name = COALESCE(#{ruleName}, rule_name), " +
            "event_type = COALESCE(#{eventType}, event_type), severity = COALESCE(#{severity}, severity), " +
            "condition_json = COALESCE(#{conditionJson}, condition_json), action = COALESCE(#{action}, action), " +
            "status = COALESCE(#{status}, status) WHERE rule_code = #{ruleCode}")
    int updateRule(@Param("ruleCode") String ruleCode,
                   @Param("ruleName") String ruleName,
                   @Param("eventType") String eventType,
                   @Param("severity") String severity,
                   @Param("conditionJson") String conditionJson,
                   @Param("action") String action,
                   @Param("status") Integer status);

    @Delete("DELETE FROM mas_security_rule WHERE rule_code = #{ruleCode}")
    int deleteRule(@Param("ruleCode") String ruleCode);

    // ---------------- 事件写入与查询 ----------------

    @Insert("""
            INSERT INTO mas_security_event
              (event_id, trace_id, tenant_id, user_id, app_id, asset_id, event_type, event_level,
               guardrail_stage, rule_id, rule_name, masked, blocked, reason_code, reason_text,
               log_storage_type, hash_signature, created_at)
            VALUES
              (#{eventId}, #{traceId}, #{tenantId}, #{userId}, #{appId}, #{assetId}, #{eventType}, #{eventLevel},
               #{guardrailStage}, #{ruleId}, #{ruleName}, #{masked}, #{blocked}, #{reasonCode}, #{reasonText},
               #{logStorageType}, #{hashSignature}, COALESCE(#{createdAt}, CURRENT_TIMESTAMP))
            ON CONFLICT (event_id) DO NOTHING
            """)
    int insertEvent(@Param("eventId") String eventId,
                    @Param("traceId") String traceId,
                    @Param("tenantId") String tenantId,
                    @Param("userId") String userId,
                    @Param("appId") String appId,
                    @Param("assetId") String assetId,
                    @Param("eventType") String eventType,
                    @Param("eventLevel") String eventLevel,
                    @Param("guardrailStage") String guardrailStage,
                    @Param("ruleId") String ruleId,
                    @Param("ruleName") String ruleName,
                    @Param("masked") Boolean masked,
                    @Param("blocked") Boolean blocked,
                    @Param("reasonCode") String reasonCode,
                    @Param("reasonText") String reasonText,
                    @Param("logStorageType") String logStorageType,
                    @Param("hashSignature") String hashSignature,
                    @Param("createdAt") LocalDateTime createdAt);

    @Select({"<script>",
            "SELECT event_id, trace_id, tenant_id, user_id, app_id, asset_id, event_type, event_level,",
            "  guardrail_stage, rule_id, rule_name, masked, blocked, reason_code, reason_text,",
            "  log_storage_type, hash_signature, created_at",
            "FROM mas_security_event WHERE 1=1",
            "<if test='eventType != null'> AND event_type = #{eventType}</if>",
            "<if test='eventLevel != null'> AND event_level = #{eventLevel}</if>",
            "<if test='tenantId != null'> AND tenant_id = #{tenantId}</if>",
            "<if test='userId != null'> AND user_id = #{userId}</if>",
            "<if test='since != null'> AND created_at &gt;= #{since}</if>",
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<Map<String, Object>> listEvents(@Param("eventType") String eventType,
                                         @Param("eventLevel") String eventLevel,
                                         @Param("tenantId") String tenantId,
                                         @Param("userId") String userId,
                                         @Param("since") LocalDateTime since,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    @Select({"<script>",
            "SELECT COUNT(*) FROM mas_security_event WHERE 1=1",
            "<if test='eventType != null'> AND event_type = #{eventType}</if>",
            "<if test='eventLevel != null'> AND event_level = #{eventLevel}</if>",
            "<if test='since != null'> AND created_at &gt;= #{since}</if>",
            "</script>"})
    long countEvents(@Param("eventType") String eventType,
                     @Param("eventLevel") String eventLevel,
                     @Param("since") LocalDateTime since);

    @Select("SELECT event_id FROM mas_security_event WHERE trace_id = #{traceId} AND rule_id = #{ruleId} LIMIT 1")
    String existsEvent(@Param("traceId") String traceId,
                       @Param("ruleId") String ruleId);

    // ---------------- 检测输入：窗口聚合 mas_call_log ----------------

    @Select("""
            SELECT user_id, app_id, tenant_id, COUNT(*) AS cnt
            FROM mas_call_log
            WHERE created_at >= #{since}
            GROUP BY user_id, app_id, tenant_id
            HAVING COUNT(*) >= #{threshold}
            """)
    List<Map<String, Object>> detectHighFrequency(@Param("since") LocalDateTime since,
                                                  @Param("threshold") long threshold);

    @Select("""
            SELECT user_id, app_id, tenant_id, COALESCE(SUM(total_tokens),0) AS tokens
            FROM mas_call_log
            WHERE created_at >= #{since}
            GROUP BY user_id, app_id, tenant_id
            HAVING COALESCE(SUM(total_tokens),0) >= #{threshold}
            """)
    List<Map<String, Object>> detectTokenSurge(@Param("since") LocalDateTime since,
                                               @Param("threshold") long threshold);

    @Select("""
            SELECT user_id, app_id, tenant_id, COUNT(*) AS cnt
            FROM mas_call_log
            WHERE created_at >= #{since}
              AND (EXTRACT(HOUR FROM created_at) >= 22 OR EXTRACT(HOUR FROM created_at) < 6)
            GROUP BY user_id, app_id, tenant_id
            HAVING COUNT(*) >= #{threshold}
            """)
    List<Map<String, Object>> detectOffHour(@Param("since") LocalDateTime since,
                                            @Param("threshold") long threshold);

    @Select("""
            SELECT trace_id, user_id, app_id, tenant_id, model_id, created_at
            FROM mas_call_log
            WHERE status = 3 AND created_at >= #{since}
            """)
    List<Map<String, Object>> detectBlocked(@Param("since") LocalDateTime since);
}

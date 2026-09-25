package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 护栏配置与安全策略（公告二-6 内容治理管理面）：
 * 此前后端返回硬编码常量、写操作只返回 opRecord 不落库，策略与词库无法在线维护。
 */
@Mapper
public interface GuardrailMapper {

    @Select("SELECT id, enabled, default_model, sensitivity, modules_json, updated_by, updated_at " +
            "FROM mas_guardrail_config ORDER BY id DESC LIMIT 1")
    Map<String, Object> selectConfig();

    @Insert("INSERT INTO mas_guardrail_config (enabled, default_model, sensitivity, modules_json, updated_by) " +
            "VALUES (COALESCE(#{enabled},1), #{defaultModel}, COALESCE(#{sensitivity},'MEDIUM'), " +
            "#{modulesJson}, #{updatedBy})")
    int insertConfig(@Param("enabled") Integer enabled,
                     @Param("defaultModel") String defaultModel,
                     @Param("sensitivity") String sensitivity,
                     @Param("modulesJson") String modulesJson,
                     @Param("updatedBy") String updatedBy);

    @Update("UPDATE mas_guardrail_config SET enabled = COALESCE(#{enabled}, enabled), " +
            "default_model = COALESCE(#{defaultModel}, default_model), " +
            "sensitivity = COALESCE(#{sensitivity}, sensitivity), " +
            "modules_json = COALESCE(#{modulesJson}, modules_json), " +
            "updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP")
    int updateConfig(@Param("enabled") Integer enabled,
                     @Param("defaultModel") String defaultModel,
                     @Param("sensitivity") String sensitivity,
                     @Param("modulesJson") String modulesJson,
                     @Param("updatedBy") String updatedBy);

    @Select("SELECT id, policy_id, name, stage, action, lib_type, keyword_lib, enabled, hit_count, " +
            "created_at, updated_at FROM mas_guardrail_policy ORDER BY id")
    List<Map<String, Object>> listPolicies();

    @Select("SELECT id, policy_id, name, stage, action, lib_type, keyword_lib, enabled, hit_count " +
            "FROM mas_guardrail_policy WHERE policy_id = #{policyId}")
    Map<String, Object> selectPolicy(@Param("policyId") String policyId);

    @Insert("""
            INSERT INTO mas_guardrail_policy (policy_id, name, stage, action, lib_type, keyword_lib, enabled, updated_by)
            VALUES (#{policyId}, #{name}, COALESCE(#{stage},'INPUT'), COALESCE(#{action},'MASK'),
                    COALESCE(#{libType},'SYSTEM'), #{keywordLib}, COALESCE(#{enabled},1), #{updatedBy})
            ON CONFLICT (policy_id) DO NOTHING
            """)
    int insertPolicy(@Param("policyId") String policyId,
                     @Param("name") String name,
                     @Param("stage") String stage,
                     @Param("action") String action,
                     @Param("libType") String libType,
                     @Param("keywordLib") String keywordLib,
                     @Param("enabled") Integer enabled,
                     @Param("updatedBy") String updatedBy);

    @Update("UPDATE mas_guardrail_policy SET name = COALESCE(#{name}, name), " +
            "stage = COALESCE(#{stage}, stage), action = COALESCE(#{action}, action), " +
            "lib_type = COALESCE(#{libType}, lib_type), keyword_lib = COALESCE(#{keywordLib}, keyword_lib), " +
            "enabled = COALESCE(#{enabled}, enabled), updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE policy_id = #{policyId}")
    int updatePolicy(@Param("policyId") String policyId,
                     @Param("name") String name,
                     @Param("stage") String stage,
                     @Param("action") String action,
                     @Param("libType") String libType,
                     @Param("keywordLib") String keywordLib,
                     @Param("enabled") Integer enabled,
                     @Param("updatedBy") String updatedBy);

    @Delete("DELETE FROM mas_guardrail_policy WHERE policy_id = #{policyId}")
    int deletePolicy(@Param("policyId") String policyId);
}

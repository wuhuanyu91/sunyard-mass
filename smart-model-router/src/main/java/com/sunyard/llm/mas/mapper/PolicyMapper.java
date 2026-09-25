package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 统一控制面策略治理（需求概览 5.2/六章）：策略 → 版本 → 审批 → 发布 → 回滚，以及请求级执行留痕。
 */
@Mapper
public interface PolicyMapper {

    @Select({"<script>",
            "SELECT id, policy_id, name, category, scope, status, current_version, owner, created_at, updated_at",
            "FROM mas_policy WHERE 1=1",
            "<if test='category != null'> AND category = #{category}</if>",
            "<if test='status != null'> AND status = #{status}</if>",
            "ORDER BY updated_at DESC",
            "</script>"})
    List<Map<String, Object>> listPolicies(@Param("category") String category,
                                           @Param("status") String status);

    @Select("SELECT id, policy_id, name, category, scope, status, current_version, owner " +
            "FROM mas_policy WHERE policy_id = #{policyId}")
    Map<String, Object> selectPolicy(@Param("policyId") String policyId);

    @Insert("INSERT INTO mas_policy (policy_id, name, category, scope, status, current_version, owner) " +
            "VALUES (#{policyId}, #{name}, #{category}, #{scope}, 'DRAFT', 0, #{owner}) " +
            "ON CONFLICT (policy_id) DO NOTHING")
    int insertPolicy(@Param("policyId") String policyId,
                     @Param("name") String name,
                     @Param("category") String category,
                     @Param("scope") String scope,
                     @Param("owner") String owner);

    @Update("UPDATE mas_policy SET name = COALESCE(#{name}, name), category = COALESCE(#{category}, category), " +
            "scope = COALESCE(#{scope}, scope), status = COALESCE(#{status}, status), " +
            "current_version = COALESCE(#{currentVersion}, current_version), updated_at = CURRENT_TIMESTAMP " +
            "WHERE policy_id = #{policyId}")
    int updatePolicy(@Param("policyId") String policyId,
                     @Param("name") String name,
                     @Param("category") String category,
                     @Param("scope") String scope,
                     @Param("status") String status,
                     @Param("currentVersion") Integer currentVersion);

    // ---------------- 版本 ----------------

    @Select("SELECT id, policy_id, version, content_json, status, submitter, approver, approve_comment, " +
            "approved_at, published_at, rolled_back_at, created_at FROM mas_policy_version " +
            "WHERE policy_id = #{policyId} ORDER BY version DESC")
    List<Map<String, Object>> listVersions(@Param("policyId") String policyId);

    @Select("SELECT COALESCE(MAX(version), 0) FROM mas_policy_version WHERE policy_id = #{policyId}")
    int maxVersion(@Param("policyId") String policyId);

    @Insert("INSERT INTO mas_policy_version (policy_id, version, content_json, status, submitter) " +
            "VALUES (#{policyId}, #{version}, #{contentJson}, 'DRAFT', #{submitter}) " +
            "ON CONFLICT (policy_id, version) DO UPDATE SET content_json = EXCLUDED.content_json")
    int insertVersion(@Param("policyId") String policyId,
                      @Param("version") int version,
                      @Param("contentJson") String contentJson,
                      @Param("submitter") String submitter);

    @Update("UPDATE mas_policy_version SET status = #{status}, " +
            "approver = COALESCE(#{approver}, approver), " +
            "approve_comment = COALESCE(#{comment}, approve_comment), " +
            "approved_at = CASE WHEN #{status} IN ('PENDING','PUBLISHED','ROLLED_BACK') THEN CURRENT_TIMESTAMP ELSE approved_at END, " +
            "published_at = CASE WHEN #{status} = 'PUBLISHED' THEN CURRENT_TIMESTAMP ELSE published_at END, " +
            "rolled_back_at = CASE WHEN #{status} = 'ROLLED_BACK' THEN CURRENT_TIMESTAMP ELSE rolled_back_at END " +
            "WHERE policy_id = #{policyId} AND version = #{version}")
    int updateVersionStatus(@Param("policyId") String policyId,
                            @Param("version") int version,
                            @Param("status") String status,
                            @Param("approver") String approver,
                            @Param("comment") String comment);

    @Select("SELECT id, policy_id, version, content_json, status FROM mas_policy_version " +
            "WHERE policy_id = #{policyId} AND version = #{version}")
    Map<String, Object> selectVersion(@Param("policyId") String policyId,
                                      @Param("version") int version);

    // ---------------- 执行留痕（POC 第 11 问：单次请求执行了哪些策略） ----------------

    @Insert("INSERT INTO mas_policy_exec_log (trace_id, policy_id, version, stage, decision, detail) " +
            "VALUES (#{traceId}, #{policyId}, #{version}, #{stage}, #{decision}, #{detail})")
    int insertExecLog(@Param("traceId") String traceId,
                      @Param("policyId") String policyId,
                      @Param("version") int version,
                      @Param("stage") String stage,
                      @Param("decision") String decision,
                      @Param("detail") String detail);

    @Select("SELECT id, trace_id, policy_id, version, stage, decision, detail, created_at " +
            "FROM mas_policy_exec_log WHERE trace_id = #{traceId} ORDER BY id")
    List<Map<String, Object>> listExecLogs(@Param("traceId") String traceId);
}

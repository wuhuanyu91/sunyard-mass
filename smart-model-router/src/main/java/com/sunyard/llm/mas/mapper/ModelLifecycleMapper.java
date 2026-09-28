package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 模型生命周期（需求概览模型资产中心）：版本 / 血缘 / 灰度发布与回滚
 */
@Mapper
public interface ModelLifecycleMapper {

    // ---------------- 版本 ----------------

    @Select("SELECT id, model_id, version, source_type, base_version, status, config_json, created_at " +
            "FROM mas_model_version WHERE model_id = #{modelId} ORDER BY version DESC")
    List<Map<String, Object>> listVersions(@Param("modelId") String modelId);

    @Insert("INSERT INTO mas_model_version (model_id, version, source_type, base_version, status, config_json) " +
            "VALUES (#{modelId}, #{version}, #{sourceType}, #{baseVersion}, COALESCE(#{status},'ONLINE'), #{configJson}) " +
            "ON CONFLICT (model_id, version) DO NOTHING")
    int insertVersion(@Param("modelId") String modelId,
                      @Param("version") String version,
                      @Param("sourceType") String sourceType,
                      @Param("baseVersion") String baseVersion,
                      @Param("status") String status,
                      @Param("configJson") String configJson);

    @Update("UPDATE mas_model_version SET status = #{status} WHERE model_id = #{modelId} AND version = #{version}")
    int updateVersionStatus(@Param("modelId") String modelId,
                            @Param("version") String version,
                            @Param("status") String status);

    // ---------------- 血缘 ----------------

    @Select("SELECT id, child_model, child_version, parent_model, parent_version, relation, created_at " +
            "FROM mas_model_lineage WHERE child_model = #{modelId} OR parent_model = #{modelId} ORDER BY id DESC")
    List<Map<String, Object>> listLineage(@Param("modelId") String modelId);

    @Insert("INSERT INTO mas_model_lineage (child_model, child_version, parent_model, parent_version, relation) " +
            "VALUES (#{childModel}, #{childVersion}, #{parentModel}, #{parentVersion}, #{relation})")
    int insertLineage(@Param("childModel") String childModel,
                      @Param("childVersion") String childVersion,
                      @Param("parentModel") String parentModel,
                      @Param("parentVersion") String parentVersion,
                      @Param("relation") String relation);

    // ---------------- 灰度发布 ----------------

    @Select("SELECT id, release_id, model_id, from_version, to_version, gray_percent, gray_scope, status, " +
            "operator, sla_rollback_ms, created_at, updated_at FROM mas_model_release ORDER BY created_at DESC LIMIT 100")
    List<Map<String, Object>> listReleases();

    @Select("SELECT id, release_id, model_id, from_version, to_version, gray_percent, gray_scope, status, " +
            "operator FROM mas_model_release WHERE release_id = #{releaseId}")
    Map<String, Object> selectRelease(@Param("releaseId") String releaseId);

    /**
     * 运行时灰度快照：只取仍在进行中（GRAYING）或已全量（FULL）的发布单。
     * ROLLBACK / ROLLING_BACK / ABORTED 不在此列 —— 撤回立即在运行时生效。
     */
    @Select("SELECT release_id, model_id, from_version, to_version, gray_percent, gray_scope, status " +
            "FROM mas_model_release WHERE status IN ('GRAYING','FULL') ORDER BY created_at DESC")
    List<Map<String, Object>> listActiveReleases();

    @Insert("INSERT INTO mas_model_release (release_id, model_id, from_version, to_version, gray_percent, " +
            "gray_scope, status, operator, sla_rollback_ms) " +
            "VALUES (#{releaseId}, #{modelId}, #{fromVersion}, #{toVersion}, COALESCE(#{grayPercent},0), " +
            "#{grayScope}, 'GRAYING', #{operator}, #{slaRollbackMs}) ON CONFLICT (release_id) DO NOTHING")
    int insertRelease(@Param("releaseId") String releaseId,
                      @Param("modelId") String modelId,
                      @Param("fromVersion") String fromVersion,
                      @Param("toVersion") String toVersion,
                      @Param("grayPercent") Integer grayPercent,
                      @Param("grayScope") String grayScope,
                      @Param("operator") String operator,
                      @Param("slaRollbackMs") Integer slaRollbackMs);

    @Update("UPDATE mas_model_release SET gray_percent = COALESCE(#{grayPercent}, gray_percent), " +
            "status = COALESCE(#{status}, status), updated_at = CURRENT_TIMESTAMP WHERE release_id = #{releaseId}")
    int updateRelease(@Param("releaseId") String releaseId,
                      @Param("grayPercent") Integer grayPercent,
                      @Param("status") String status);
}

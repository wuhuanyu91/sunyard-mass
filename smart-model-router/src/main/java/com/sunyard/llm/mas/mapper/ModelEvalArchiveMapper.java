package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 模型评测与归档（需求概览第十章）：
 * 【改造背景】前端 getEvals / getArchivedModels / getArchiveRules 全部是内存 mock，
 * 后端无评测表、无归档表、无自动归档规则，模型下线也没有依赖检查。
 */
@Mapper
public interface ModelEvalArchiveMapper {

    // ---------------- 评测 ----------------

    /** 评测价值聚合（ROI 投入产出分析用）：次数/均分/通过数 */
    @Select("SELECT COUNT(*) AS eval_count, COALESCE(AVG(score),0) AS avg_score, " +
            "COUNT(CASE WHEN conclusion='PASS' THEN 1 END) AS pass_count FROM mas_model_eval")
    Map<String, Object> evalAggregate();

    @Insert("""
            INSERT INTO mas_model_eval
              (eval_id, model_id, version, eval_type, dataset, accuracy, task_success_rate,
               human_accept_rate, avg_latency_ms, token_cost, anomaly_rate, compliance_rate,
               score, conclusion, operator)
            VALUES
              (#{evalId}, #{modelId}, #{version}, #{evalType}, #{dataset}, #{accuracy},
               #{taskSuccessRate}, #{humanAcceptRate}, #{avgLatencyMs}, #{tokenCost},
               #{anomalyRate}, #{complianceRate}, #{score}, #{conclusion}, #{operator})
            ON CONFLICT (eval_id) DO UPDATE SET
               accuracy = EXCLUDED.accuracy, task_success_rate = EXCLUDED.task_success_rate,
               human_accept_rate = EXCLUDED.human_accept_rate, avg_latency_ms = EXCLUDED.avg_latency_ms,
               token_cost = EXCLUDED.token_cost, anomaly_rate = EXCLUDED.anomaly_rate,
               compliance_rate = EXCLUDED.compliance_rate, score = EXCLUDED.score,
               conclusion = EXCLUDED.conclusion
            """)
    int upsertEval(@Param("evalId") String evalId,
                   @Param("modelId") String modelId,
                   @Param("version") String version,
                   @Param("evalType") String evalType,
                   @Param("dataset") String dataset,
                   @Param("accuracy") java.math.BigDecimal accuracy,
                   @Param("taskSuccessRate") java.math.BigDecimal taskSuccessRate,
                   @Param("humanAcceptRate") java.math.BigDecimal humanAcceptRate,
                   @Param("avgLatencyMs") Integer avgLatencyMs,
                   @Param("tokenCost") java.math.BigDecimal tokenCost,
                   @Param("anomalyRate") java.math.BigDecimal anomalyRate,
                   @Param("complianceRate") java.math.BigDecimal complianceRate,
                   @Param("score") java.math.BigDecimal score,
                   @Param("conclusion") String conclusion,
                   @Param("operator") String operator);

    @Select("SELECT eval_id, model_id, version, eval_type, dataset, accuracy, task_success_rate, " +
            "human_accept_rate, avg_latency_ms, token_cost, anomaly_rate, compliance_rate, " +
            "score, conclusion, operator, created_at FROM mas_model_eval " +
            "WHERE (#{modelId, jdbcType=VARCHAR} IS NULL OR model_id = #{modelId, jdbcType=VARCHAR}) " +
            "ORDER BY created_at DESC LIMIT 200")
    List<Map<String, Object>> listEvals(@Param("modelId") String modelId);

    // ---------------- 归档 ----------------

    @Insert("""
            INSERT INTO mas_model_archive
              (archive_id, model_id, model_name, reason, retention, value_score,
               score_cost, score_conversion, score_risk_acc, dependency_apps, archived_by)
            VALUES
              (#{archiveId}, #{modelId}, #{modelName}, #{reason}, #{retention}, #{valueScore},
               #{scoreCost}, #{scoreConversion}, #{scoreRiskAcc}, #{dependencyApps}, #{archivedBy})
            ON CONFLICT (archive_id) DO NOTHING
            """)
    int insertArchive(@Param("archiveId") String archiveId,
                      @Param("modelId") String modelId,
                      @Param("modelName") String modelName,
                      @Param("reason") String reason,
                      @Param("retention") String retention,
                      @Param("valueScore") String valueScore,
                      @Param("scoreCost") java.math.BigDecimal scoreCost,
                      @Param("scoreConversion") java.math.BigDecimal scoreConversion,
                      @Param("scoreRiskAcc") java.math.BigDecimal scoreRiskAcc,
                      @Param("dependencyApps") String dependencyApps,
                      @Param("archivedBy") String archivedBy);

    @Select("SELECT archive_id, model_id, model_name, reason, retention, value_score, " +
            "score_cost, score_conversion, score_risk_acc, dependency_apps, archived_by, " +
            "archived_at, revived_at FROM mas_model_archive ORDER BY archived_at DESC LIMIT 200")
    List<Map<String, Object>> listArchives();

    /** 按模型取最新一条未复活归档（此前为全量 listArchives 再 Java 过滤） */
    @Select("SELECT archive_id FROM mas_model_archive " +
            "WHERE model_id = #{modelId} AND revived_at IS NULL " +
            "ORDER BY archived_at DESC LIMIT 1")
    String latestArchiveIdByModel(@Param("modelId") String modelId);

    @Update("UPDATE mas_model_archive SET revived_at = #{at} WHERE archive_id = #{archiveId}")
    int markRevived(@Param("archiveId") String archiveId, @Param("at") LocalDateTime at);

    @Update("DELETE FROM mas_model_archive WHERE archive_id = #{archiveId} AND retention <> 'PERMANENT'")
    int deleteArchive(@Param("archiveId") String archiveId);

    // ---------------- 自动归档规则 ----------------

    @Select("SELECT rule_id, rule_key, rule_name, enabled, action, threshold_days " +
            "FROM mas_archive_rule ORDER BY rule_id")
    List<Map<String, Object>> listArchiveRules();

    @Update("UPDATE mas_archive_rule SET enabled = #{enabled}, action = #{action}, " +
            "threshold_days = COALESCE(#{thresholdDays}, threshold_days), updated_by = #{operator}, " +
            "updated_at = CURRENT_TIMESTAMP WHERE rule_id = #{ruleId}")
    int updateArchiveRule(@Param("ruleId") String ruleId,
                          @Param("enabled") Integer enabled,
                          @Param("action") String action,
                          @Param("thresholdDays") Integer thresholdDays,
                          @Param("operator") String operator);

    // ---------------- 下线依赖检查 ----------------

    /**
     * 模型下线依赖检查：统计近 N 天仍在调用该模型的应用及调用量。
     * 【改造背景】此前下线前无任何依赖检查，直接下线会静默打断在用应用。
     */
    @Select("""
            SELECT app_id,
                   COUNT(*)                     AS calls,
                   MAX(created_at)              AS last_call_at
            FROM mas_call_log
            WHERE model_id = #{modelId}
              AND created_at >= CURRENT_TIMESTAMP - (#{days} || ' days')::interval
            GROUP BY app_id
            ORDER BY calls DESC
            """)
    List<Map<String, Object>> dependentApps(@Param("modelId") String modelId, @Param("days") int days);

    /** 自动归档规则命中扫描：超过 N 天无调用的模型 */
    @Select("""
            SELECT model_id,
                   COUNT(*)          AS calls,
                   MAX(created_at)   AS last_call_at
            FROM mas_call_log
            GROUP BY model_id
            HAVING MAX(created_at) < CURRENT_TIMESTAMP - (#{days} || ' days')::interval
            """)
    List<Map<String, Object>> staleModels(@Param("days") int days);
}

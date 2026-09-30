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
 * 分级采集上报通道（公告一-1）+ 算力消耗归集（公告一-3）
 */
@Mapper
public interface CollectionMapper {

    // ---------------- 采集点 ----------------

    @Select("SELECT id, source_code, source_name, source_level, protocol, endpoint_url, status, " +
            "last_report_at, created_at FROM mas_collection_source ORDER BY id")
    List<Map<String, Object>> listSources();

    @Insert("INSERT INTO mas_collection_source (source_code, source_name, source_level, protocol, " +
            "endpoint_url, push_token, status) " +
            "VALUES (#{sourceCode}, #{sourceName}, #{sourceLevel}, COALESCE(#{protocol},'HTTP'), " +
            "#{endpointUrl}, #{pushToken}, COALESCE(#{status},1)) ON CONFLICT (source_code) DO NOTHING")
    int insertSource(@Param("sourceCode") String sourceCode,
                     @Param("sourceName") String sourceName,
                     @Param("sourceLevel") String sourceLevel,
                     @Param("protocol") String protocol,
                     @Param("endpointUrl") String endpointUrl,
                     @Param("pushToken") String pushToken,
                     @Param("status") Integer status);

    @Update("UPDATE mas_collection_source SET source_name = COALESCE(#{sourceName}, source_name), " +
            "source_level = COALESCE(#{sourceLevel}, source_level), status = COALESCE(#{status}, status), " +
            "endpoint_url = COALESCE(#{endpointUrl}, endpoint_url), last_report_at = CURRENT_TIMESTAMP " +
            "WHERE source_code = #{sourceCode}")
    int updateSource(@Param("sourceCode") String sourceCode,
                     @Param("sourceName") String sourceName,
                     @Param("sourceLevel") String sourceLevel,
                     @Param("status") Integer status,
                     @Param("endpointUrl") String endpointUrl);

    @Select("SELECT push_token FROM mas_collection_source WHERE source_code = #{sourceCode} AND status = 1")
    String tokenOfSource(@Param("sourceCode") String sourceCode);

    // ---------------- 上报批次 ----------------

    @Insert("INSERT INTO mas_collection_batch (batch_no, source_code, record_count, status, report_time) " +
            "VALUES (#{batchNo}, #{sourceCode}, #{recordCount}, 'RECEIVED', #{reportTime}) " +
            "ON CONFLICT (batch_no) DO NOTHING")
    int insertBatch(@Param("batchNo") String batchNo,
                    @Param("sourceCode") String sourceCode,
                    @Param("recordCount") int recordCount,
                    @Param("reportTime") LocalDateTime reportTime);

    @Update("UPDATE mas_collection_batch SET accepted_count = #{accepted}, rejected_count = #{rejected}, " +
            "status = #{status} WHERE batch_no = #{batchNo}")
    int updateBatch(@Param("batchNo") String batchNo,
                    @Param("accepted") int accepted,
                    @Param("rejected") int rejected,
                    @Param("status") String status);

    @Select("SELECT id, batch_no, source_code, record_count, accepted_count, rejected_count, status, " +
            "report_time, created_at FROM mas_collection_batch ORDER BY created_at DESC LIMIT 100")
    List<Map<String, Object>> listBatches();

    // ---------------- 算力指标 ----------------

    @Insert("""
            INSERT INTO mas_compute_metric
              (node_id, metric_time, gpu_util, gpu_mem_util, gpu_hours, requests, tokens,
               vram_total_gb, vram_used_gb, instance_count, queue_depth, source)
            VALUES
              (#{nodeId}, #{metricTime}, #{gpuUtil}, #{gpuMemUtil}, #{gpuHours}, #{requests}, #{tokens},
               #{vramTotalGb}, #{vramUsedGb}, #{instanceCount}, #{queueDepth}, #{source})
            ON CONFLICT (node_id, metric_time) DO UPDATE SET
              gpu_util = EXCLUDED.gpu_util, gpu_mem_util = EXCLUDED.gpu_mem_util,
              gpu_hours = EXCLUDED.gpu_hours, requests = EXCLUDED.requests, tokens = EXCLUDED.tokens,
              vram_total_gb = EXCLUDED.vram_total_gb, vram_used_gb = EXCLUDED.vram_used_gb,
              instance_count = EXCLUDED.instance_count, queue_depth = EXCLUDED.queue_depth
            """)
    int upsertComputeMetric(@Param("nodeId") String nodeId,
                            @Param("metricTime") LocalDateTime metricTime,
                            @Param("gpuUtil") java.math.BigDecimal gpuUtil,
                            @Param("gpuMemUtil") java.math.BigDecimal gpuMemUtil,
                            @Param("gpuHours") java.math.BigDecimal gpuHours,
                            @Param("requests") Integer requests,
                            @Param("tokens") Long tokens,
                            @Param("vramTotalGb") java.math.BigDecimal vramTotalGb,
                            @Param("vramUsedGb") java.math.BigDecimal vramUsedGb,
                            @Param("instanceCount") Integer instanceCount,
                            @Param("queueDepth") Integer queueDepth,
                            @Param("source") String source);

    /** 节点最新算力指标（替代请求量反推的模拟值） */
    @Select("""
            SELECT node_id,
                   COALESCE(AVG(gpu_util), 0)      AS gpu_util,
                   COALESCE(AVG(gpu_mem_util), 0)  AS gpu_mem_util,
                   COALESCE(SUM(gpu_hours), 0)     AS gpu_hours,
                   COALESCE(SUM(requests), 0)      AS requests,
                   COALESCE(SUM(tokens), 0)        AS tokens,
                   COALESCE(MAX(vram_total_gb), 0) AS vram_total_gb,
                   COALESCE(MAX(vram_used_gb), 0)  AS vram_used_gb,
                   COALESCE(MAX(instance_count), 0) AS instance_count,
                   COALESCE((ARRAY_AGG(queue_depth ORDER BY metric_time DESC))[1], 0) AS queue_depth,
                   MAX(metric_time)                AS last_metric_time
            FROM mas_compute_metric
            WHERE metric_time >= #{since}
            GROUP BY node_id
            ORDER BY node_id
            """)
    List<Map<String, Object>> latestCompute(@Param("since") LocalDateTime since);

    @Select("SELECT COALESCE(SUM(gpu_hours),0) AS total_gpu_hours, COALESCE(AVG(gpu_util),0) AS avg_gpu_util " +
            "FROM mas_compute_metric WHERE metric_time >= #{since}")
    Map<String, Object> computeSummary(@Param("since") LocalDateTime since);

    /**
     * 算力热区：按小时聚合调用量，为错峰调度提供真实依据
     * （此前"热区建议"是前端硬编码文案，无任何数据支撑）。
     */
    @Select("""
            SELECT EXTRACT(HOUR FROM created_at)::int AS hour,
                   COUNT(*)                            AS calls,
                   COALESCE(SUM(total_tokens), 0)      AS tokens
            FROM mas_call_log
            WHERE created_at >= CURRENT_TIMESTAMP - INTERVAL '24 hours'
            GROUP BY EXTRACT(HOUR FROM created_at)
            ORDER BY hour
            """)
    List<Map<String, Object>> hourlyLoad();

    /** 按厂商统计已上报节点数（异构算力自动发现） */
    @Select("SELECT COUNT(DISTINCT node_id) FROM mas_compute_metric " +
            "WHERE source = #{vendorId} AND metric_time >= CURRENT_TIMESTAMP - INTERVAL '24 hours'")
    Integer countNodesByVendor(@Param("vendorId") String vendorId);
}

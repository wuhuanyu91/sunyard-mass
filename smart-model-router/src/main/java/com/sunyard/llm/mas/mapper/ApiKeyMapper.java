package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.ApiKeyEntity;
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
 * mas_api_key Mapper（§8 鉴权体系 + §1.4 自助申请增强）
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKeyEntity> {

    @Select("SELECT id, key_hash, key_prefix, user_id, app_id, status, expire_at, created_at, " +
            "team_name, agent_name, agent_type, purpose, quota_tier, created_by " +
            "FROM mas_api_key WHERE key_hash = #{hash} AND status = 1")
    ApiKeyEntity selectByHash(@Param("hash") String hash);

    @Select("SELECT id, key_hash, key_prefix, user_id, app_id, status, expire_at, created_at, " +
            "team_name, agent_name, agent_type, purpose, quota_tier, created_by " +
            "FROM mas_api_key WHERE key_prefix = #{prefix} AND status = 1")
    ApiKeyEntity selectByPrefix(@Param("prefix") String prefix);

    /** §1.4 增强版创建：携带完整元数据 */
    @Insert("""
            INSERT INTO mas_api_key (key_hash, key_prefix, user_id, app_id, status, expire_at,
                team_name, agent_name, agent_type, purpose, quota_tier, created_by)
            VALUES (#{hash}, #{prefix}, #{userId}, #{appId}, 1, #{expireAt},
                #{teamName}, #{agentName}, #{agentType}, #{purpose}, #{quotaTier}, #{createdBy})
            """)
    int insertKeyFull(@Param("hash") String hash,
                      @Param("prefix") String prefix,
                      @Param("userId") String userId,
                      @Param("appId") String appId,
                      @Param("expireAt") LocalDateTime expireAt,
                      @Param("teamName") String teamName,
                      @Param("agentName") String agentName,
                      @Param("agentType") String agentType,
                      @Param("purpose") String purpose,
                      @Param("quotaTier") String quotaTier,
                      @Param("createdBy") String createdBy);

    /** 兼容旧签名（种子数据 / 测试用） */
    @Insert("INSERT INTO mas_api_key (key_hash, key_prefix, user_id, app_id, status, expire_at) " +
            "VALUES (#{hash}, #{prefix}, #{userId}, #{appId}, 1, #{expireAt})")
    int insertKey(@Param("hash") String hash,
                  @Param("prefix") String prefix,
                  @Param("userId") String userId,
                  @Param("appId") String appId,
                  @Param("expireAt") java.time.LocalDateTime expireAt);

    @Update("UPDATE mas_api_key SET status = 0 WHERE key_prefix = #{prefix}")
    int revokeByPrefix(@Param("prefix") String prefix);

    @Delete("DELETE FROM mas_api_key WHERE key_prefix = #{prefix}")
    int deleteByPrefix(@Param("prefix") String prefix);

    // ---- §1.4 自助申请增强查询 ----

    /** 按条件筛选 Key 列表（不返回 key_hash，仅展示 prefix + 元数据） */
    @Select({"<script>",
            "SELECT id, key_prefix, user_id, app_id, status, expire_at, created_at,",
            "  team_name, agent_name, agent_type, purpose, quota_tier, created_by",
            "FROM mas_api_key WHERE 1=1",
            "<if test='teamName != null'> AND team_name = #{teamName}</if>",
            "<if test='userId != null'> AND user_id = #{userId}</if>",
            "<if test='status != null'> AND status = #{status}</if>",
            "<if test='agentType != null'> AND agent_type = #{agentType}</if>",
            "ORDER BY created_at DESC",
            "</script>"})
    List<ApiKeyEntity> listKeys(@Param("teamName") String teamName,
                                @Param("userId") String userId,
                                @Param("status") Integer status,
                                @Param("agentType") String agentType);

    /** §1.4 批量吊销：按 team_name 或 user_id 批量吊销名下所有 Key */
    @Update({"<script>",
            "UPDATE mas_api_key SET status = 0 WHERE status = 1",
            "<if test='teamName != null'> AND team_name = #{teamName}</if>",
            "<if test='userId != null'> AND user_id = #{userId}</if>",
            "</script>"})
    int batchRevoke(@Param("teamName") String teamName,
                    @Param("userId") String userId);

    /** §1.4 Key 轮换：设置旧 Key 的 grace period 过期时间 */
    @Update("UPDATE mas_api_key SET expire_at = #{graceExpiry} WHERE key_prefix = #{prefix} AND status = 1")
    int setGraceExpiry(@Param("prefix") String prefix,
                       @Param("graceExpiry") LocalDateTime graceExpiry);

    /** §1.4 用量查询：按 user_id 聚合 mas_call_log 的 token 消耗 */
    @Select("""
            SELECT
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COUNT(*) AS call_count,
                COALESCE(SUM(CASE WHEN cache_hit = 1 THEN 1 ELSE 0 END), 0) AS cache_hits
            FROM mas_call_log
            WHERE user_id = #{userId}
              AND created_at >= #{since}
            """)
    Map<String, Object> usageSummary(@Param("userId") String userId,
                                     @Param("since") LocalDateTime since);
}

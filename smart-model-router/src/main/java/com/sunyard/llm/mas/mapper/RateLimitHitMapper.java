package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 限流规则命中记录（mas_rate_limit_hit）：
 * 【改造背景】mas_routing_rule 此前只被读出来给前端展示，规则里的 QPS / Token / 并发阈值
 * 从未进入请求链路，命中次数也是静态种子值。
 */
@Mapper
public interface RateLimitHitMapper {

    @Insert("INSERT INTO mas_rate_limit_hit (rule_id, target_type, target_id, hit_action) " +
            "VALUES (#{ruleId}, #{targetType}, #{targetId}, #{hitAction})")
    int insertHit(@Param("ruleId") String ruleId,
                  @Param("targetType") String targetType,
                  @Param("targetId") String targetId,
                  @Param("hitAction") String hitAction);

    @Select("SELECT COUNT(*) FROM mas_rate_limit_hit WHERE rule_id = #{ruleId} " +
            "AND hit_at > CURRENT_TIMESTAMP - INTERVAL '24 hours'")
    int countHits24h(@Param("ruleId") String ruleId);

    @Update("UPDATE mas_routing_rule SET hits_24h = #{hits} WHERE rule_id = #{ruleId}")
    int syncHits24h(@Param("ruleId") String ruleId, @Param("hits") int hits);

    /** 清理超过 7 天的命中流水，避免无限增长 */
    @Update("DELETE FROM mas_rate_limit_hit WHERE hit_at < CURRENT_TIMESTAMP - INTERVAL '7 days'")
    int purgeOld();
}

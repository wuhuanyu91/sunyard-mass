package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * mas_rate_limit 分布式限流 Mapper（§8 已知限制消除 — 替代 Bucket4j 内存桶）
 */
@Mapper
public interface RateLimitMapper {

    /**
     * 原子限流：插入或累加计数，仅在未超限时成功。
     * @return 影响行数，> 0 表示放行
     */
    @Insert("""
            INSERT INTO mas_rate_limit (user_id, window_key, token_count, window_end)
            VALUES (#{userId}, #{windowKey}, 1, #{windowEnd})
            ON CONFLICT (user_id, window_key) DO UPDATE
            SET token_count = mas_rate_limit.token_count + 1
            WHERE mas_rate_limit.token_count < #{limit}
            """)
    int tryAcquire(@Param("userId") String userId,
                   @Param("windowKey") String windowKey,
                   @Param("limit") int limit,
                   @Param("windowEnd") LocalDateTime windowEnd);

    /** 定时清理过期窗口行 */
    @Delete("DELETE FROM mas_rate_limit WHERE window_end < now()")
    int purgeExpired();
}

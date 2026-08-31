package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.TokenQuotaEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * mas_token_quota Token 配额 Mapper
 */
@Mapper
public interface QuotaMapper extends BaseMapper<TokenQuotaEntity> {

    @Insert("""
            INSERT INTO mas_token_quota (quota_type, quota_key, period, token_limit, token_used, reset_at)
            VALUES ('user', #{key}, #{period}, #{limit}, 0, #{reset})
            ON CONFLICT (quota_type, quota_key, period) DO UPDATE
            SET reset_at = EXCLUDED.reset_at, token_used = 0, token_limit = EXCLUDED.token_limit
            WHERE mas_token_quota.reset_at <= now()
            """)
    int upsertQuota(@Param("key") String key,
                    @Param("period") String period,
                    @Param("limit") Long limit,
                    @Param("reset") LocalDateTime reset);

    @Update("""
            UPDATE mas_token_quota
            SET token_used = token_used + #{tokens}
            WHERE quota_type = 'user' AND quota_key = #{key} AND period = #{period}
              AND reset_at > now() AND token_used + #{tokens} <= token_limit
            """)
    int updateQuotaUsed(@Param("key") String key,
                        @Param("period") String period,
                        @Param("tokens") Long tokens);

    @Update("""
            UPDATE mas_token_quota
            SET token_used = GREATEST(0, token_used - #{tokens})
            WHERE quota_type = 'user' AND quota_key = #{key} AND period = #{period}
              AND reset_at > now()
            """)
    int refundQuota(@Param("key") String key,
                    @Param("period") String period,
                    @Param("tokens") Long tokens);
}

package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.ExactCacheEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * mas_exact_cache 精确缓存 Mapper
 */
@Mapper
public interface ExactCacheMapper extends BaseMapper<ExactCacheEntity> {

    @Select("SELECT response_json FROM mas_exact_cache WHERE cache_key = #{key} AND expire_at > now()")
    String selectActive(@Param("key") String key);

    @Insert("""
            INSERT INTO mas_exact_cache (cache_key, model_id, response_json, expire_at)
            VALUES (#{key}, #{modelId}, #{responseJson}, now() + CAST(#{ttl} || ' seconds' AS INTERVAL))
            ON CONFLICT (cache_key) DO UPDATE
            SET response_json = EXCLUDED.response_json, expire_at = EXCLUDED.expire_at
            """)
    int upsert(@Param("key") String key,
               @Param("modelId") String modelId,
               @Param("responseJson") String responseJson,
               @Param("ttl") String ttl);

    @Delete("DELETE FROM mas_exact_cache")
    int deleteAll();

    @Delete("DELETE FROM mas_exact_cache WHERE expire_at < now()")
    int purgeExpired();
}

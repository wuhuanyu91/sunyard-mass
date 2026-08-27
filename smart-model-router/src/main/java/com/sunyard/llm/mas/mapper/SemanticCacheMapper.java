package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.SemanticCacheEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * mas_semantic_cache 语义缓存 Mapper（pgvector 向量检索）
 */
@Mapper
public interface SemanticCacheMapper extends BaseMapper<SemanticCacheEntity> {

    @Select("""
            SELECT response_json, 1 - (embedding <=> CAST(#{vector} AS vector)) AS similarity
            FROM mas_semantic_cache
            WHERE model_id = #{modelId} AND expire_at > now()
            ORDER BY embedding <=> CAST(#{vector} AS vector) ASC
            LIMIT 1
            """)
    Map<String, Object> searchNearest(@Param("modelId") String modelId,
                                      @Param("vector") String vector);

    @Insert("""
            INSERT INTO mas_semantic_cache (model_id, embedding, response_json, expire_at)
            VALUES (#{modelId}, CAST(#{vector} AS vector), #{responseJson},
                    now() + CAST(#{ttl} || ' seconds' AS INTERVAL))
            """)
    int insertEntry(@Param("modelId") String modelId,
                    @Param("vector") String vector,
                    @Param("responseJson") String responseJson,
                    @Param("ttl") String ttl);

    @Delete("DELETE FROM mas_semantic_cache")
    int deleteAll();

    @Delete("DELETE FROM mas_semantic_cache WHERE expire_at < now()")
    int purgeExpired();
}

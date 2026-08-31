package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.ApiKeyEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * mas_api_key Mapper（§8 已知限制消除 — 鉴权体系）
 */
@Mapper
public interface ApiKeyMapper extends BaseMapper<ApiKeyEntity> {

    @Select("SELECT id, key_hash, key_prefix, user_id, app_id, status, expire_at, created_at " +
            "FROM mas_api_key WHERE key_hash = #{hash} AND status = 1")
    ApiKeyEntity selectByHash(@Param("hash") String hash);

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
}

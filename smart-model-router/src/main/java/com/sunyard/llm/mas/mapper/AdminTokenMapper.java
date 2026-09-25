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
 * 管理端点令牌（此前 /internal/* 全部裸奔，无认证即可操作所有管理接口）
 */
@Mapper
public interface AdminTokenMapper {

    @Select("SELECT id, token_hash, user_code, role_code, status, expire_at " +
            "FROM mas_admin_token WHERE token_hash = #{hash} AND status = 1")
    Map<String, Object> selectByHash(@Param("hash") String hash);

    @Insert("INSERT INTO mas_admin_token (token_hash, user_code, role_code, status, expire_at) " +
            "VALUES (#{hash}, #{userCode}, #{roleCode}, 1, #{expireAt}) ON CONFLICT (token_hash) DO NOTHING")
    int insert(@Param("hash") String hash,
               @Param("userCode") String userCode,
               @Param("roleCode") String roleCode,
               @Param("expireAt") LocalDateTime expireAt);

    @Update("UPDATE mas_admin_token SET status = 0 WHERE token_hash = #{hash}")
    int revoke(@Param("hash") String hash);

    @Update("UPDATE mas_admin_token SET status = 0 WHERE expire_at IS NOT NULL AND expire_at < CURRENT_TIMESTAMP")
    int expireOutdated();

    @Select("SELECT id, user_code, role_code, status, expire_at, created_at FROM mas_admin_token ORDER BY id DESC LIMIT 100")
    List<Map<String, Object>> list();
}

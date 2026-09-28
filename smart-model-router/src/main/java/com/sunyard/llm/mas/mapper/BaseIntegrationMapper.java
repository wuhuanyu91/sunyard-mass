package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.BaseIntegrationEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 行内底座对接配置访问（注解式 SQL，与项目规范一致）。
 */
@Mapper
public interface BaseIntegrationMapper extends BaseMapper<BaseIntegrationEntity> {

    @Select("SELECT code, name, type, endpoint, enabled, last_sync_at, status, remark, created_at, updated_at " +
            "FROM mas_base_integration WHERE code = #{code}")
    BaseIntegrationEntity selectByCode(@Param("code") String code);

    @Update("INSERT INTO mas_base_integration (code, name, type, endpoint, enabled, last_sync_at, status, remark, updated_at) " +
            "VALUES (#{code}, #{name}, #{type}, #{endpoint}, #{enabled}, #{lastSyncAt}, COALESCE(#{status}, 'PENDING'), #{remark}, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type, " +
            "endpoint = EXCLUDED.endpoint, enabled = EXCLUDED.enabled, " +
            "last_sync_at = COALESCE(EXCLUDED.last_sync_at, mas_base_integration.last_sync_at), " +
            "status = EXCLUDED.status, remark = EXCLUDED.remark, updated_at = CURRENT_TIMESTAMP")
    int upsert(BaseIntegrationEntity e);

    @Update("UPDATE mas_base_integration SET status = #{status}, updated_at = CURRENT_TIMESTAMP WHERE code = #{code}")
    int updateStatus(@Param("code") String code, @Param("status") String status);

    @Update("UPDATE mas_base_integration SET status = #{status}, remark = #{remark}, updated_at = CURRENT_TIMESTAMP WHERE code = #{code}")
    int updateStatusWithRemark(@Param("code") String code, @Param("status") String status, @Param("remark") String remark);
}

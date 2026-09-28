package com.sunyard.llm.mas.mapper;

import com.sunyard.llm.mas.entity.PlatformConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 平台配置 KV 访问（注解式 SQL，与项目规范一致）
 */
@Mapper
public interface PlatformConfigMapper extends com.baomidou.mybatisplus.core.mapper.BaseMapper<PlatformConfigEntity> {

    @Select("SELECT config_key, config_json, operator, updated_at FROM mas_platform_config WHERE config_key = #{configKey}")
    PlatformConfigEntity selectByKey(@Param("configKey") String configKey);

    @Update("INSERT INTO mas_platform_config (config_key, config_json, operator, updated_at) " +
            "VALUES (#{configKey}, #{configJson}, #{operator}, CURRENT_TIMESTAMP) " +
            "ON CONFLICT (config_key) DO UPDATE SET config_json = EXCLUDED.config_json, " +
            "operator = EXCLUDED.operator, updated_at = CURRENT_TIMESTAMP")
    int upsert(@Param("configKey") String configKey,
               @Param("configJson") String configJson,
               @Param("operator") String operator);
}

package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.ModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * mas_model_config 模型配置 Mapper
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfigEntity> {

    @Select("SELECT model_id, model_name, provider, endpoint_url, intent_type, weight, status, max_context_tokens FROM mas_model_config")
    List<ModelConfigEntity> selectAll();
}

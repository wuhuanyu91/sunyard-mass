package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.BlacklistEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * mas_blacklist 黑名单 Mapper
 */
@Mapper
public interface BlacklistMapper extends BaseMapper<BlacklistEntity> {

    @Select("SELECT subject_key FROM mas_blacklist WHERE subject_type = 'user' AND (expire_at IS NULL OR expire_at > now())")
    List<String> selectActiveSubjectKeys();
}

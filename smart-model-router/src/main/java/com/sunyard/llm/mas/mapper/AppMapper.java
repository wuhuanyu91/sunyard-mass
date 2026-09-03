package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.AppEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * mas_app Mapper（§1.5 应用身份统一管控）
 */
@Mapper
public interface AppMapper extends BaseMapper<AppEntity> {

    @Select({"<script>",
            "SELECT app_id, app_name, app_name_en, dept_id, owner_id, owner_email,",
            "  sla_level, data_level, status, month_quota, description,",
            "  approved_by, approved_at, created_at, updated_at",
            "FROM mas_app WHERE 1=1",
            "<if test='status != null'> AND status = #{status}</if>",
            "<if test='deptId != null'> AND dept_id = #{deptId}</if>",
            "ORDER BY created_at DESC",
            "</script>"})
    List<AppEntity> listApps(@Param("status") Integer status,
                             @Param("deptId") String deptId);

    @Select("SELECT app_id, app_name, app_name_en, dept_id, owner_id, owner_email," +
            "  sla_level, data_level, status, month_quota, description," +
            "  approved_by, approved_at, created_at, updated_at" +
            " FROM mas_app WHERE app_id = #{appId}")
    AppEntity selectByAppId(@Param("appId") String appId);

    @Insert("""
            INSERT INTO mas_app (app_id, app_name, app_name_en, dept_id, owner_id, owner_email,
                sla_level, data_level, status, month_quota, description)
            VALUES (#{appId}, #{appName}, #{appNameEn}, #{deptId}, #{ownerId}, #{ownerEmail},
                #{slaLevel}, #{dataLevel}, 1, #{monthQuota}, #{description})
            """)
    int insertApp(@Param("appId") String appId,
                  @Param("appName") String appName,
                  @Param("appNameEn") String appNameEn,
                  @Param("deptId") String deptId,
                  @Param("ownerId") String ownerId,
                  @Param("ownerEmail") String ownerEmail,
                  @Param("slaLevel") String slaLevel,
                  @Param("dataLevel") String dataLevel,
                  @Param("monthQuota") Long monthQuota,
                  @Param("description") String description);

    @Update("""
            UPDATE mas_app SET app_name = #{appName}, app_name_en = #{appNameEn},
                dept_id = #{deptId}, owner_id = #{ownerId}, owner_email = #{ownerEmail},
                sla_level = #{slaLevel}, data_level = #{dataLevel},
                month_quota = #{monthQuota}, description = #{description},
                updated_at = now()
            WHERE app_id = #{appId}
            """)
    int updateApp(@Param("appId") String appId,
                  @Param("appName") String appName,
                  @Param("appNameEn") String appNameEn,
                  @Param("deptId") String deptId,
                  @Param("ownerId") String ownerId,
                  @Param("ownerEmail") String ownerEmail,
                  @Param("slaLevel") String slaLevel,
                  @Param("dataLevel") String dataLevel,
                  @Param("monthQuota") Long monthQuota,
                  @Param("description") String description);

    @Update("UPDATE mas_app SET status = #{status}, updated_at = now() WHERE app_id = #{appId}")
    int updateStatus(@Param("appId") String appId, @Param("status") Integer status);
}

package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.TenantEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 租户与映射（公告二-1/二-2）：mas_tenant / mas_dept_tenant / mas_app_tenant
 * 用于消除 CallLogService.appToTenant、MeteringService.deptToTenant 两处硬编码 switch。
 */
@Mapper
public interface TenantMapper extends BaseMapper<TenantEntity> {

    @Select("SELECT id, tenant_id, tenant_name, status, isolation_mode, quota_tokens, contact, created_at " +
            "FROM mas_tenant ORDER BY id")
    List<Map<String, Object>> listTenants();

    @Select("SELECT id, tenant_id, tenant_name, status, isolation_mode, quota_tokens, contact " +
            "FROM mas_tenant WHERE tenant_id = #{tenantId}")
    Map<String, Object> selectTenant(@Param("tenantId") String tenantId);

    @Insert("INSERT INTO mas_tenant (tenant_id, tenant_name, status, isolation_mode, quota_tokens, contact) " +
            "VALUES (#{tenantId}, #{tenantName}, COALESCE(#{status},1), COALESCE(#{isolationMode},'RLS'), " +
            "#{quotaTokens}, #{contact}) ON CONFLICT (tenant_id) DO NOTHING")
    int insertTenant(@Param("tenantId") String tenantId,
                     @Param("tenantName") String tenantName,
                     @Param("status") Integer status,
                     @Param("isolationMode") String isolationMode,
                     @Param("quotaTokens") Long quotaTokens,
                     @Param("contact") String contact);

    @Update("UPDATE mas_tenant SET tenant_name = COALESCE(#{tenantName}, tenant_name), " +
            "status = COALESCE(#{status}, status), isolation_mode = COALESCE(#{isolationMode}, isolation_mode), " +
            "quota_tokens = COALESCE(#{quotaTokens}, quota_tokens), contact = COALESCE(#{contact}, contact) " +
            "WHERE tenant_id = #{tenantId}")
    int updateTenant(@Param("tenantId") String tenantId,
                     @Param("tenantName") String tenantName,
                     @Param("status") Integer status,
                     @Param("isolationMode") String isolationMode,
                     @Param("quotaTokens") Long quotaTokens,
                     @Param("contact") String contact);

    @Delete("DELETE FROM mas_tenant WHERE tenant_id = #{tenantId}")
    int deleteTenant(@Param("tenantId") String tenantId);

    // ---------------- 部门 ↔ 租户 ----------------

    @Select("SELECT dept_id, dept_name, tenant_id FROM mas_dept_tenant ORDER BY dept_id")
    List<Map<String, Object>> listDeptTenants();

    @Select("SELECT tenant_id FROM mas_dept_tenant WHERE dept_id = #{deptId}")
    String tenantOfDept(@Param("deptId") String deptId);

    @Insert("INSERT INTO mas_dept_tenant (dept_id, dept_name, tenant_id) VALUES (#{deptId}, #{deptName}, #{tenantId}) " +
            "ON CONFLICT (dept_id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id, dept_name = EXCLUDED.dept_name")
    int upsertDeptTenant(@Param("deptId") String deptId,
                         @Param("deptName") String deptName,
                         @Param("tenantId") String tenantId);

    // ---------------- 应用 ↔ 租户 ----------------

    @Select("SELECT app_id, tenant_id FROM mas_app_tenant ORDER BY app_id")
    List<Map<String, Object>> listAppTenants();

    @Select("SELECT tenant_id FROM mas_app_tenant WHERE app_id = #{appId}")
    String tenantOfApp(@Param("appId") String appId);

    @Insert("INSERT INTO mas_app_tenant (app_id, tenant_id) VALUES (#{appId}, #{tenantId}) " +
            "ON CONFLICT (app_id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id")
    int upsertAppTenant(@Param("appId") String appId,
                        @Param("tenantId") String tenantId);
}

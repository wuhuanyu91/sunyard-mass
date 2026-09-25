package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.SysUserEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * RBAC 统一 Mapper（公告二-4 权限控制）：用户 / 角色 / 权限 / 用户角色 / 角色权限矩阵
 * 采用注解式 SQL，跨表操作集中在本接口，避免表数量膨胀带来的 Mapper 碎片化。
 */
@Mapper
public interface RbacMapper extends BaseMapper<SysUserEntity> {

    // ---------------- 用户 ----------------

    @Select({"<script>",
            "SELECT id, user_code, user_name, dept_id, tenant_id, email, phone, status, locked,",
            "  fail_count, pwd_must_change, mfa_enabled, last_login_at, created_at, updated_at",
            "FROM mas_sys_user WHERE 1=1",
            "<if test='keyword != null and keyword != \"\"'> AND (user_code ILIKE CONCAT('%',#{keyword},'%') OR user_name ILIKE CONCAT('%',#{keyword},'%'))</if>",
            "<if test='status != null'> AND status = #{status}</if>",
            "ORDER BY created_at DESC",
            "</script>"})
    List<Map<String, Object>> listUsers(@Param("keyword") String keyword,
                                        @Param("status") Integer status);

    @Select("SELECT id, user_code, user_name, dept_id, tenant_id, email, phone, status, locked, " +
            "fail_count, pwd_must_change, mfa_enabled, last_login_at FROM mas_sys_user WHERE user_code = #{userCode}")
    Map<String, Object> selectUser(@Param("userCode") String userCode);

    @Insert("INSERT INTO mas_sys_user (user_code, user_name, dept_id, tenant_id, email, phone, status, locked, " +
            "fail_count, pwd_hash, pwd_must_change, mfa_enabled) " +
            "VALUES (#{userCode}, #{userName}, #{deptId}, #{tenantId}, #{email}, #{phone}, " +
            "COALESCE(#{status},1), 0, 0, #{pwdHash}, COALESCE(#{pwdMustChange},0), COALESCE(#{mfaEnabled},0)) " +
            "ON CONFLICT (user_code) DO NOTHING")
    int insertUser(@Param("userCode") String userCode,
                   @Param("userName") String userName,
                   @Param("deptId") String deptId,
                   @Param("tenantId") String tenantId,
                   @Param("email") String email,
                   @Param("phone") String phone,
                   @Param("status") Integer status,
                   @Param("pwdHash") String pwdHash,
                   @Param("pwdMustChange") Integer pwdMustChange,
                   @Param("mfaEnabled") Integer mfaEnabled);

    @Update("UPDATE mas_sys_user SET user_name = COALESCE(#{userName}, user_name), " +
            "dept_id = COALESCE(#{deptId}, dept_id), tenant_id = COALESCE(#{tenantId}, tenant_id), " +
            "email = COALESCE(#{email}, email), phone = COALESCE(#{phone}, phone), " +
            "status = COALESCE(#{status}, status), updated_at = CURRENT_TIMESTAMP " +
            "WHERE user_code = #{userCode}")
    int updateUser(@Param("userCode") String userCode,
                   @Param("userName") String userName,
                   @Param("deptId") String deptId,
                   @Param("tenantId") String tenantId,
                   @Param("email") String email,
                   @Param("phone") String phone,
                   @Param("status") Integer status);

    /** 启用/停用、解锁、重置密码、强制改密、双因素 —— 统一状态变更入口 */
    @Update("UPDATE mas_sys_user SET status = COALESCE(#{status}, status), " +
            "locked = COALESCE(#{locked}, locked), fail_count = COALESCE(#{failCount}, fail_count), " +
            "pwd_hash = COALESCE(#{pwdHash}, pwd_hash), " +
            "pwd_must_change = COALESCE(#{pwdMustChange}, pwd_must_change), " +
            "mfa_enabled = COALESCE(#{mfaEnabled}, mfa_enabled), updated_at = CURRENT_TIMESTAMP " +
            "WHERE user_code = #{userCode}")
    int updateUserState(@Param("userCode") String userCode,
                        @Param("status") Integer status,
                        @Param("locked") Integer locked,
                        @Param("failCount") Integer failCount,
                        @Param("pwdHash") String pwdHash,
                        @Param("pwdMustChange") Integer pwdMustChange,
                        @Param("mfaEnabled") Integer mfaEnabled);

    @Delete("DELETE FROM mas_sys_user WHERE user_code = #{userCode}")
    int deleteUser(@Param("userCode") String userCode);

    // ---------------- 角色 ----------------

    @Select("SELECT id, role_code, role_name, builtin, description, created_at FROM mas_sys_role ORDER BY builtin DESC, id")
    List<Map<String, Object>> listRoles();

    @Insert("INSERT INTO mas_sys_role (role_code, role_name, builtin, description) " +
            "VALUES (#{roleCode}, #{roleName}, COALESCE(#{builtin},0), #{description}) " +
            "ON CONFLICT (role_code) DO NOTHING")
    int insertRole(@Param("roleCode") String roleCode,
                   @Param("roleName") String roleName,
                   @Param("builtin") Integer builtin,
                   @Param("description") String description);

    @Update("UPDATE mas_sys_role SET role_name = COALESCE(#{roleName}, role_name), " +
            "description = COALESCE(#{description}, description) WHERE role_code = #{roleCode}")
    int updateRole(@Param("roleCode") String roleCode,
                   @Param("roleName") String roleName,
                   @Param("description") String description);

    @Delete("DELETE FROM mas_sys_role WHERE role_code = #{roleCode} AND builtin = 0")
    int deleteRole(@Param("roleCode") String roleCode);

    // ---------------- 权限 ----------------

    @Select("SELECT id, perm_code, module, perm_name FROM mas_sys_permission ORDER BY module, id")
    List<Map<String, Object>> listPermissions();

    @Insert("INSERT INTO mas_sys_permission (perm_code, module, perm_name) " +
            "VALUES (#{permCode}, #{module}, #{permName}) ON CONFLICT (perm_code) DO NOTHING")
    int insertPermission(@Param("permCode") String permCode,
                         @Param("module") String module,
                         @Param("permName") String permName);

    /** 权限矩阵：模块 × 角色 的四级授权（DENY/READ/WRITE/ADMIN） */
    @Select("SELECT role_code, module, perm_level FROM mas_sys_role_permission")
    List<Map<String, Object>> listRolePermissions();

    @Insert("INSERT INTO mas_sys_role_permission (role_code, module, perm_level) " +
            "VALUES (#{roleCode}, #{module}, #{permLevel}) " +
            "ON CONFLICT (role_code, module) DO UPDATE SET perm_level = EXCLUDED.perm_level")
    int upsertRolePermission(@Param("roleCode") String roleCode,
                             @Param("module") String module,
                             @Param("permLevel") String permLevel);

    @Delete("DELETE FROM mas_sys_role_permission WHERE role_code = #{roleCode} AND module = #{module}")
    int deleteRolePermission(@Param("roleCode") String roleCode,
                             @Param("module") String module);

    // ---------------- 用户 × 角色 ----------------

    @Select("SELECT user_code, role_code FROM mas_sys_user_role")
    List<Map<String, Object>> listUserRoles();

    @Insert("INSERT INTO mas_sys_user_role (user_code, role_code) VALUES (#{userCode}, #{roleCode}) " +
            "ON CONFLICT (user_code, role_code) DO NOTHING")
    int insertUserRole(@Param("userCode") String userCode,
                       @Param("roleCode") String roleCode);

    @Delete("DELETE FROM mas_sys_user_role WHERE user_code = #{userCode} AND role_code = #{roleCode}")
    int deleteUserRole(@Param("userCode") String userCode,
                       @Param("roleCode") String roleCode);

    /** 成员管理视图：用户 + 角色 + 部门 */
    @Select({"<script>",
            "SELECT u.user_code, u.user_name, u.dept_id, u.status, u.last_login_at,",
            "  COALESCE(STRING_AGG(r.role_code, ','), '') AS roles",
            "FROM mas_sys_user u LEFT JOIN mas_sys_user_role ur ON ur.user_code = u.user_code",
            "LEFT JOIN mas_sys_role r ON r.role_code = ur.role_code",
            "GROUP BY u.user_code, u.user_name, u.dept_id, u.status, u.last_login_at",
            "ORDER BY u.user_code",
            "</script>"})
    List<Map<String, Object>> listMembers();

    /** 鉴权判定：某用户对某模块的实际权限级别（取所有角色中最高） */
    @Select("""
            SELECT CASE MAX(CASE p.perm_level
                    WHEN 'ADMIN' THEN 4 WHEN 'WRITE' THEN 3 WHEN 'READ' THEN 2 ELSE 1 END)
                   WHEN 4 THEN 'ADMIN' WHEN 3 THEN 'WRITE' WHEN 2 THEN 'READ' ELSE 'DENY' END AS level
            FROM mas_sys_user_role ur
            JOIN mas_sys_role_permission p ON p.role_code = ur.role_code
            WHERE ur.user_code = #{userCode} AND p.module = #{module}
            """)
    String resolvePermission(@Param("userCode") String userCode,
                             @Param("module") String module);
}

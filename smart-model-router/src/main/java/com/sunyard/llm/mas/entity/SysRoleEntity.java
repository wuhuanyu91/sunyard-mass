package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 系统角色（公告二-4 权限控制 RBAC）
 */
@TableName("mas_sys_role")
public class SysRoleEntity {

    @TableId
    private Long id;
    private String roleCode;
    private String roleName;
    private Integer builtin;      // 1=内置角色，不可删除
    private String description;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public Integer getBuiltin() { return builtin; }
    public void setBuiltin(Integer builtin) { this.builtin = builtin; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

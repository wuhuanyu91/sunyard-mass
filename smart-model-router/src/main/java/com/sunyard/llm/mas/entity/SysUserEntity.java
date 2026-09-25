package com.sunyard.llm.mas.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 系统用户（公告二-4 权限控制 RBAC）
 */
@TableName("mas_sys_user")
public class SysUserEntity {

    @TableId
    private Long id;
    private String userCode;
    private String userName;
    private String deptId;
    private String tenantId;
    private String email;
    private String phone;
    private Integer status;          // 1=正常 0=禁用
    private Integer locked;          // 1=锁定
    private Integer failCount;
    private String pwdHash;
    private Integer pwdMustChange;
    private Integer mfaEnabled;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUserCode() { return userCode; }
    public void setUserCode(String userCode) { this.userCode = userCode; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getDeptId() { return deptId; }
    public void setDeptId(String deptId) { this.deptId = deptId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getLocked() { return locked; }
    public void setLocked(Integer locked) { this.locked = locked; }
    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
    public String getPwdHash() { return pwdHash; }
    public void setPwdHash(String pwdHash) { this.pwdHash = pwdHash; }
    public Integer getPwdMustChange() { return pwdMustChange; }
    public void setPwdMustChange(Integer pwdMustChange) { this.pwdMustChange = pwdMustChange; }
    public Integer getMfaEnabled() { return mfaEnabled; }
    public void setMfaEnabled(Integer mfaEnabled) { this.mfaEnabled = mfaEnabled; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

package com.yo.auth.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_user")
public class UserPO {
  private Long id;
  private Long orgId;
  private String username;
  private String passwordHash;
  private String displayName;
  private Boolean enabled;
  private Long tokenVersion;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getOrgId() {
    return orgId;
  }

  public void setOrgId(Long orgId) {
    this.orgId = orgId;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public Boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(Boolean enabled) {
    this.enabled = enabled;
  }

  public Long getTokenVersion() {
    return tokenVersion;
  }

  public void setTokenVersion(Long tokenVersion) {
    this.tokenVersion = tokenVersion;
  }
}

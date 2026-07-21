package com.yo.security.web;

public final class UserHeaders {
  public static final String USER_ID = "X-User-Id";
  public static final String ORGANIZATION_ID = "X-Organization-Id";
  public static final String USERNAME = "X-Username";
  public static final String ROLES = "X-User-Roles";
  public static final String PERMISSIONS = "X-User-Permissions";
  public static final String INTERNAL_CALL = "X-Internal-Call";
  public static final String INTERNAL_TIMESTAMP = "X-Internal-Timestamp";
  public static final String INTERNAL_NONCE = "X-Internal-Nonce";
  public static final String INTERNAL_SIGNATURE = "X-Internal-Signature";

  private UserHeaders() {}
}

package com.yo.security.context;

import java.util.Set;

public record CurrentUser(
    Long userId,
    Long organizationId,
    String username,
    Set<String> roles,
    Set<String> permissions) {

  public CurrentUser {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean hasPermission(String permission) {
    return permissions.contains(permission);
  }
}

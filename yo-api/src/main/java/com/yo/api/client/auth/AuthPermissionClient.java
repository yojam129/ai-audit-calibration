package com.yo.api.client.auth;

import com.yo.api.config.FeignInternalConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "auth-service",
    contextId = "authPermissionClient",
    configuration = FeignInternalConfiguration.class)
public interface AuthPermissionClient {
  @PostMapping("/internal/auth/permissions/freeze")
  void freeze(@RequestBody PermissionChange request);

  @PostMapping("/internal/auth/permissions/restore")
  void restore(@RequestBody PermissionChange request);

  record PermissionChange(
      String operationId,
      Long authUserId,
      String permissionCode,
      String reason,
      Long approvedByAuthUserId) {}
}

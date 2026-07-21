package com.yo.auth.domain.dto;
import jakarta.validation.constraints.*;
public record PermissionChangeDTO(
    @NotBlank String operationId,
    @NotNull Long authUserId,
    @NotBlank String permissionCode,
    @NotBlank String reason,
    Long approvedByAuthUserId) {}

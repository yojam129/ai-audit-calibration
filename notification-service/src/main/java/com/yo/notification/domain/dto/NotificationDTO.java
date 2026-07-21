package com.yo.notification.domain.dto;

import jakarta.validation.constraints.*;

public record NotificationDTO(
    @NotBlank String requestId,
    @NotBlank String userId,
    @Email String email,
    @NotBlank String subject,
    @NotBlank String body,
    boolean emailRequested) {}

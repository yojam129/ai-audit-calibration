package com.yo.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutDTO(@NotBlank String refreshToken) {}

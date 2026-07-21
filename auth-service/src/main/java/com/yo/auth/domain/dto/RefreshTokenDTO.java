package com.yo.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenDTO(@NotBlank String refreshToken) {}

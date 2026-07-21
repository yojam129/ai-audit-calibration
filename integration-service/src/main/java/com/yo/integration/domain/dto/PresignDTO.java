package com.yo.integration.domain.dto;

import jakarta.validation.constraints.*;

public record PresignDTO(
    @NotBlank String fileName,
    @NotBlank String contentType,
    @Positive long sizeBytes,
    @NotBlank String sha256) {}

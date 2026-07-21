package com.yo.integration.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfirmUploadDTO(
    @NotNull Long assetId, @Positive long sizeBytes, @NotBlank String sha256) {}

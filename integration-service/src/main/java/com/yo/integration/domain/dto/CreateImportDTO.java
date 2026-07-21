package com.yo.integration.domain.dto;

import jakarta.validation.constraints.*;

public record CreateImportDTO(
    @NotNull Long assetId, @NotBlank String businessType, @NotBlank String templateVersion) {}

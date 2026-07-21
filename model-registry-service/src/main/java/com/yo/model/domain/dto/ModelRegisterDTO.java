package com.yo.model.domain.dto;

import jakarta.validation.constraints.*;

public record ModelRegisterDTO(
    @NotBlank String modelCode,
    @NotBlank String version,
    @NotBlank String runtime,
    @NotBlank String artifactUri,
    @NotBlank String checksum,
    String metricsJson) {}

package com.yo.learning.domain.dto;

import jakarta.validation.constraints.*;

public record LearningDTO(
    @NotBlank String reviewerId,
    @NotNull Long authUserId,
    @NotBlank String courseCode,
    @NotBlank String errorType,
    @Min(1) int dueDays) {}

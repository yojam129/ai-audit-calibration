package com.yo.sample.domain.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record CreateSampleDTO(
    @NotBlank String organizationId,
    String externalNo,
    @NotBlank String specimenType,
    LocalDateTime collectedAt) {}

package com.yo.integration.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportRecoveryDTO {
  @NotBlank private String resolution;
  private String reason;
}

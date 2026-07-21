package com.yo.signal.domain.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record StoreCurveDTO(
    @NotBlank String runNo,
    @Pattern(regexp = "A|B") String chamber,
    @NotBlank String channelCode,
    @NotBlank String targetCode,
    @NotBlank String processingVersion,
    @NotEmpty List<@NotNull Double> rawValues,
    List<Double> correctedValues,
    Double ctValue,
    @PositiveOrZero Double concentrationValue,
    String concentrationUnit,
    String riskLevel,
    List<String> riskFlags) {}

package com.yo.trace.domain.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record AppendTraceDTO(
    @NotBlank String eventId,
    @NotBlank String eventType,
    @NotBlank String aggregateType,
    @NotBlank String aggregateId,
    @NotBlank String actorId,
    @NotBlank String organizationId,
    @NotBlank String traceId,
    String payloadJson,
    @NotNull Instant occurredAt) {}

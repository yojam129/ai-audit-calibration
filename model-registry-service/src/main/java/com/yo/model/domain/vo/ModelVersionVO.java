package com.yo.model.domain.vo;

import java.time.LocalDateTime;

public record ModelVersionVO(
    Long id,
    String modelCode,
    String version,
    String runtime,
    String artifactUri,
    String checksum,
    String status,
    Integer trafficPercent,
    LocalDateTime createdAt) {}

package com.yo.sample.domain.vo;

import java.time.*;

public record SampleVO(
    Long id,
    String businessId,
    String sampleNo,
    String organizationId,
    String externalNo,
    String specimenType,
    String status,
    LocalDateTime collectedAt) {}

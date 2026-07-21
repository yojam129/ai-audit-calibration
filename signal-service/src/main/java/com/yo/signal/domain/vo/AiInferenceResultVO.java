package com.yo.signal.domain.vo;

import java.time.LocalDateTime;

public record AiInferenceResultVO(
    long id,
    String curveId,
    String runNo,
    String chamber,
    String targetCode,
    String status,
    String judgement,
    Double confidence,
    String evidenceJson,
    String modelVersion,
    String failureReason,
    LocalDateTime updatedAt) {}

package com.yo.signal.domain.vo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record FullCurveVO(
    String id,
    String runNo,
    String chamber,
    String channelCode,
    String targetCode,
    String processingVersion,
    List<Double> rawValues,
    List<Double> correctedValues,
    Map<String, Double> features,
    String qcStatus,
    String checksum,
    Instant createdAt) {}

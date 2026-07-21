package com.yo.signal.domain.vo;

import java.util.*;

public record CurveVO(
    String id,
    String runNo,
    String chamber,
    String channelCode,
    String processingVersion,
    int pointCount,
    String qcStatus,
    Map<String, Double> features,
    String checksum) {}

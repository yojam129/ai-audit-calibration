package com.yo.sample.domain.vo;

public record ImportAggregateVO(
    long sampleId, long orderId, long runId, String runNo, boolean created) {}

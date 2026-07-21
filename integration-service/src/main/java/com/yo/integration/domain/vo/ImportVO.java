package com.yo.integration.domain.vo;

import java.time.LocalDateTime;

public record ImportVO(
    Long id,
    String batchNo,
    String businessType,
    String status,
    int totalRows,
    int successRows,
    int errorRows,
    String failureReason,
    String processInstanceId,
    String flowableTaskId,
    LocalDateTime createdAt) {}

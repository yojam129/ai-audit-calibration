package com.yo.learning.domain.vo;

import java.time.Instant;

public record PermissionRestoreApplicationVO(
    long assignmentId,
    String reviewerId,
    String courseCode,
    String errorType,
    String focusSampleId,
    String focusSampleNo,
    String focusChamber,
    String focusChannelCode,
    String focusTargetCode,
    double bestScore,
    String status,
    Instant appliedAt,
    String processInstanceId,
    Instant workflowStartedAt) {}

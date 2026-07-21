package com.yo.notification.domain.vo;

import java.time.Instant;

public record NotificationVO(
    long id,
    String requestId,
    String userId,
    String subject,
    String status,
    boolean read,
    Instant createdAt,
    Instant sentAt) {}

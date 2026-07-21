package com.yo.notification.domain.vo;

import java.util.Set;

public record NotificationPreferenceVO(
    String userId,
    String email,
    boolean inAppEnabled,
    boolean emailEnabled,
    Set<String> eventTypes) {}

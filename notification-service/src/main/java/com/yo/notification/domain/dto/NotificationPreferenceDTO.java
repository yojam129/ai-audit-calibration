package com.yo.notification.domain.dto;

import jakarta.validation.constraints.Email;
import java.util.Set;

public record NotificationPreferenceDTO(
    @Email String email, boolean inAppEnabled, boolean emailEnabled, Set<String> eventTypes) {}

package com.yo.notification.service;

import com.yo.notification.domain.dto.NotificationPreferenceDTO;
import com.yo.notification.domain.vo.NotificationPreferenceVO;

public interface NotificationPreferenceService {
  NotificationPreferenceVO get(String userId);

  NotificationPreferenceVO update(String userId, NotificationPreferenceDTO dto);

  Decision decide(String userId, String eventType);

  record Decision(boolean inApp, boolean email, String emailAddress) {}
}

package com.yo.notification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.yo.notification.mapper.NotificationPreferenceMapper;
import com.yo.notification.service.impl.NotificationPreferenceServiceImpl;
import org.junit.jupiter.api.Test;

class NotificationPreferenceServiceTest {
  @Test
  void defaultPolicyIsInAppOnly() {
    var mapper = mock(NotificationPreferenceMapper.class);
    var service = new NotificationPreferenceServiceImpl(mapper);
    var decision = service.decide("new-user", "alert.created.v1");
    assertTrue(decision.inApp());
    assertFalse(decision.email());
  }
}

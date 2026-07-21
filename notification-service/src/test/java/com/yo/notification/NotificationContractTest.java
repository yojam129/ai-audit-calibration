package com.yo.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.yo.notification.domain.dto.NotificationDTO;
import org.junit.jupiter.api.Test;

class NotificationContractTest {
  @Test
  void inAppDoesNotRequireEmail() {
    assertFalse(new NotificationDTO("r", "u", null, "s", "b", false).emailRequested());
  }
}

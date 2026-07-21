package com.yo.api.client.notification;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import java.time.LocalDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.NOTIFICATION,
    contextId = "notificationClient",
    configuration = FeignInternalConfiguration.class)
public interface NotificationClient {
  @PostMapping("/api/v1/notifications")
  NotificationVO send(@RequestBody NotificationRequest request);

  record NotificationRequest(
      String requestId,
      String recipientId,
      String channel,
      String subject,
      String content,
      String email) {}

  record NotificationVO(long id, String requestId, String status, LocalDateTime createdAt) {}
}

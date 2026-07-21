package com.yo.notification.mq;

import com.yo.notification.domain.dto.NotificationDTO;
import com.yo.notification.service.*;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class DomainNotificationListener {
  private final NotificationService notifications;
  private final NotificationPreferenceService preferences;

  public DomainNotificationListener(NotificationService n, NotificationPreferenceService p) {
    notifications = n;
    preferences = p;
  }

  @RabbitListener(queues = "${app.queue.notification:notification.domain.v1}")
  public void receive(
      Map<String, Object> event, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String type) {
    String id = String.valueOf(event.getOrDefault("eventId", event.getOrDefault("requestId", "")));
    if (id.isBlank()) throw new IllegalArgumentException("eventId required");
    String user =
        String.valueOf(event.getOrDefault("ownerId", event.getOrDefault("reviewerId", "system")));
    var decision = preferences.decide(user, type);
    if (!decision.inApp() && !decision.email()) return;
    String subject =
        switch (type) {
          case "training.triggered.v1" -> "校准培训任务";
          case "ground-truth.confirmed.v1" -> "复核真值已确认";
          default -> "审核预警通知";
        };
    Object supplied = event.get("email");
    String email =
        decision.emailAddress() != null
            ? decision.emailAddress()
            : supplied == null ? null : String.valueOf(supplied);
    notifications.send(
        new NotificationDTO(id, user, email, subject, subject + "，事件编号：" + id, decision.email()));
  }
}

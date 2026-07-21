package com.yo.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class NotificationRabbitConfig {
  @Bean
  TopicExchange domain() {
    return new TopicExchange("ai.audit.domain", true, false);
  }

  @Bean
  DirectExchange dlx() {
    return new DirectExchange("ai.audit.dlx", true, false);
  }

  @Bean
  Queue notifications() {
    return QueueBuilder.durable("notification.domain.v1")
        .deadLetterExchange("ai.audit.dlx")
        .deadLetterRoutingKey("notification.domain.v1.dlq")
        .build();
  }

  @Bean
  Queue notificationDlq() {
    return QueueBuilder.durable("notification.domain.v1.dlq").build();
  }

  @Bean
  Binding alertBinding(Queue notifications, TopicExchange domain) {
    return BindingBuilder.bind(notifications).to(domain).with("alert.#");
  }

  @Bean
  Binding reviewBinding(Queue notifications, TopicExchange domain) {
    return BindingBuilder.bind(notifications).to(domain).with("ground-truth.#");
  }

  @Bean
  Binding riskBinding(Queue notifications, TopicExchange domain) {
    return BindingBuilder.bind(notifications).to(domain).with("training.#");
  }

  @Bean
  Binding dlqBinding(Queue notificationDlq, DirectExchange dlx) {
    return BindingBuilder.bind(notificationDlq).to(dlx).with("notification.domain.v1.dlq");
  }
}

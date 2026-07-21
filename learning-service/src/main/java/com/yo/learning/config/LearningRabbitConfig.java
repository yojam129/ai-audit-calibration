package com.yo.learning.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class LearningRabbitConfig {
  @Bean
  TopicExchange auditDomainExchange() {
    return new TopicExchange("ai.audit.domain", true, false);
  }

  @Bean
  Queue trainingQueue() {
    return QueueBuilder.durable("learning.training-trigger.v1").build();
  }

  @Bean
  Binding trainingBinding(Queue trainingQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(trainingQueue).to(auditDomainExchange).with("training.triggered.v1");
  }
}

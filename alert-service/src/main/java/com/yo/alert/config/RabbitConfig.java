package com.yo.alert.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RabbitConfig {
  @Bean
  Jackson2JsonMessageConverter rabbitJsonMessageConverter(ObjectMapper mapper) {
    return new Jackson2JsonMessageConverter(mapper);
  }
  @Bean
  TopicExchange auditDomainExchange() {
    return ExchangeBuilder.topicExchange("ai.audit.domain").durable(true).build();
  }

  @Bean
  Queue comparisonQueue() {
    return QueueBuilder.durable("alert.comparison.v1")
        .deadLetterExchange("ai.audit.dlx")
        .deadLetterRoutingKey("alert.comparison.v1.dlq")
        .build();
  }

  @Bean
  Queue detectionTargetQueue() {
    return QueueBuilder.durable("alert.detection-target.v1")
        .deadLetterExchange("ai.audit.dlx")
        .deadLetterRoutingKey("alert.detection-target.v1.dlq")
        .build();
  }

  @Bean
  DirectExchange auditDeadLetterExchange() {
    return ExchangeBuilder.directExchange("ai.audit.dlx").durable(true).build();
  }

  @Bean
  Queue comparisonDeadLetterQueue() {
    return QueueBuilder.durable("alert.comparison.v1.dlq").build();
  }

  @Bean
  Binding comparisonDeadLetterBinding(
      Queue comparisonDeadLetterQueue, DirectExchange auditDeadLetterExchange) {
    return BindingBuilder.bind(comparisonDeadLetterQueue)
        .to(auditDeadLetterExchange)
        .with("alert.comparison.v1.dlq");
  }

  @Bean
  Binding comparisonBinding(Queue comparisonQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(comparisonQueue)
        .to(auditDomainExchange)
        .with("comparison.completed.v1");
  }

  @Bean
  Binding detectionTargetBinding(
      Queue detectionTargetQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(detectionTargetQueue)
        .to(auditDomainExchange)
        .with("detection.target.completed.v1");
  }
}

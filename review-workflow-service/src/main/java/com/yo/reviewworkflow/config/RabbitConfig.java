package com.yo.reviewworkflow.config;

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
  Queue reviewComparisonQueue() {
    return QueueBuilder.durable("review.comparison.v1").build();
  }

  @Bean
  Binding reviewComparisonBinding(Queue reviewComparisonQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(reviewComparisonQueue)
        .to(auditDomainExchange)
        .with("comparison.completed.v1");
  }

  @Bean
  Queue auditImportedQueue() {
    return QueueBuilder.durable("review.audit.imported.v1").build();
  }

  @Bean
  Queue auditAiCompletedQueue() {
    return QueueBuilder.durable("review.audit.ai-completed.v1").build();
  }

  @Bean
  Queue auditPrimaryCompletedQueue() {
    return QueueBuilder.durable("review.audit.primary-completed.v1").build();
  }

  @Bean
  Binding auditImportedBinding(Queue auditImportedQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(auditImportedQueue)
        .to(auditDomainExchange)
        .with("sample.audit.import-completed.v1");
  }

  @Bean
  Binding auditAiCompletedBinding(Queue auditAiCompletedQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(auditAiCompletedQueue)
        .to(auditDomainExchange)
        .with("sample.audit.ai-completed.v1");
  }

  @Bean
  Binding auditPrimaryCompletedBinding(
      Queue auditPrimaryCompletedQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(auditPrimaryCompletedQueue)
        .to(auditDomainExchange)
        .with("sample.audit.primary-completed.v1");
  }
}

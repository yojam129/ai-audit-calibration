package com.yo.sample.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
  Queue sampleReviewArchivedQueue() {
    return QueueBuilder.durable("sample.review.archived.v1").build();
  }

  @Bean
  Binding sampleReviewArchivedBinding(
      Queue sampleReviewArchivedQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(sampleReviewArchivedQueue)
        .to(auditDomainExchange)
        .with("sample.review.archived.v1");
  }

  @Bean
  Queue sampleAiInferenceQueue() {
    return QueueBuilder.durable("sample.ai-inference.completed.v1").build();
  }

  @Bean
  Binding sampleAiInferenceBinding(
      Queue sampleAiInferenceQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(sampleAiInferenceQueue)
        .to(auditDomainExchange)
        .with("ai.inference.completed.v1");
  }
}

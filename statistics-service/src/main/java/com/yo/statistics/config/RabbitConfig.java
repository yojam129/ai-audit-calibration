package com.yo.statistics.config;

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
  Queue truthQueue() {
    return QueueBuilder.durable("statistics.truth.v1")
        .deadLetterExchange("ai.audit.dlx")
        .deadLetterRoutingKey("statistics.truth.v1.dlq")
        .build();
  }

  @Bean
  DirectExchange auditDeadLetterExchange() {
    return ExchangeBuilder.directExchange("ai.audit.dlx").durable(true).build();
  }

  @Bean
  Queue truthDeadLetterQueue() {
    return QueueBuilder.durable("statistics.truth.v1.dlq").build();
  }

  @Bean
  Binding truthDeadLetterBinding(
      Queue truthDeadLetterQueue, DirectExchange auditDeadLetterExchange) {
    return BindingBuilder.bind(truthDeadLetterQueue)
        .to(auditDeadLetterExchange)
        .with("statistics.truth.v1.dlq");
  }

  @Bean
  Binding truthBinding(Queue truthQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(truthQueue).to(auditDomainExchange).with("ground-truth.confirmed.v1");
  }
}

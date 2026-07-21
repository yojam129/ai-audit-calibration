package com.yo.judgement.config;

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
}

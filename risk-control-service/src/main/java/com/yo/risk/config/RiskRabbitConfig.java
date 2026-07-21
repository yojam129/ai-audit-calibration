package com.yo.risk.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RiskRabbitConfig {
  @Bean
  Jackson2JsonMessageConverter rabbitJsonMessageConverter(ObjectMapper mapper) {
    return new Jackson2JsonMessageConverter(mapper);
  }
  @Bean
  TopicExchange auditDomainExchange() {
    return new TopicExchange("ai.audit.domain", true, false);
  }

  @Bean
  Queue reviewerOutcomeQueue() {
    return QueueBuilder.durable("risk.reviewer-outcome.v1").build();
  }

  @Bean
  Binding reviewerOutcomeBinding(Queue reviewerOutcomeQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(reviewerOutcomeQueue)
        .to(auditDomainExchange)
        .with("reviewer.outcome.v1");
  }

  @Bean
  Queue groundTruthRiskQueue() {
    return QueueBuilder.durable("risk.ground-truth.v1").build();
  }

  @Bean
  Binding groundTruthRiskBinding(Queue groundTruthRiskQueue, TopicExchange auditDomainExchange) {
    return BindingBuilder.bind(groundTruthRiskQueue)
        .to(auditDomainExchange)
        .with("ground-truth.confirmed.v1");
  }
}

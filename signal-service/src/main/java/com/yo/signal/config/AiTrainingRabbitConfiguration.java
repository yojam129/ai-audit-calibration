package com.yo.signal.config;

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
public class AiTrainingRabbitConfiguration {
  @Bean
  TopicExchange aiTrainingDomainExchange() {
    return ExchangeBuilder.topicExchange("ai.audit.domain").durable(true).build();
  }

  @Bean
  Queue aiTrainingFeedbackQueue() {
    return QueueBuilder.durable("signal.ai-training-feedback.v1").build();
  }

  @Bean
  Binding aiTrainingFeedbackBinding(
      Queue aiTrainingFeedbackQueue, TopicExchange aiTrainingDomainExchange) {
    return BindingBuilder.bind(aiTrainingFeedbackQueue)
        .to(aiTrainingDomainExchange)
        .with("ground-truth.confirmed.v1");
  }

  @Bean
  Jackson2JsonMessageConverter aiTrainingRabbitJsonConverter(ObjectMapper mapper) {
    return new Jackson2JsonMessageConverter(mapper);
  }
}

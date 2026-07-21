package com.yo.scheduler.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;

@Configuration
public class InternalClientConfiguration {
  @Bean
  @LoadBalanced
  RestClient.Builder internalRestClient() {
    return RestClient.builder();
  }
}

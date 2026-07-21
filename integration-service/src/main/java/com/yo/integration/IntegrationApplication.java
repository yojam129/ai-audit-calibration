package com.yo.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableFeignClients(basePackages = {"com.yo.api.client", "com.yo.integration.client"})
@EnableScheduling
@SpringBootApplication
public class IntegrationApplication {
  public static void main(String[] args) {
    SpringApplication.run(IntegrationApplication.class, args);
  }
}

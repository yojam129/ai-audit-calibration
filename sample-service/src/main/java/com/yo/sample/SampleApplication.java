package com.yo.sample;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.yo.api.client")
public class SampleApplication {
  public static void main(String[] a) {
    SpringApplication.run(SampleApplication.class, a);
  }
}

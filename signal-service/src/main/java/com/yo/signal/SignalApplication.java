package com.yo.signal;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.yo.api.client")
@EnableScheduling
public class SignalApplication {
  public static void main(String[] a) {
    SpringApplication.run(SignalApplication.class, a);
  }
}

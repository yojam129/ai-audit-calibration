package com.yo.learning;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import com.yo.api.annotations.EnableYoFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableYoFeignClients
@SpringBootApplication
@EnableScheduling
public class LearningApplication {
  public static void main(String[] a) {
    SpringApplication.run(LearningApplication.class, a);
  }
}

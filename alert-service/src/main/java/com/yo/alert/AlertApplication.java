package com.yo.alert;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlertApplication {
  public static void main(String[] a) {
    SpringApplication.run(AlertApplication.class, a);
  }
}

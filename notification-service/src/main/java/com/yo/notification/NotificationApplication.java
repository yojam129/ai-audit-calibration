package com.yo.notification;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NotificationApplication {
  public static void main(String[] a) {
    SpringApplication.run(NotificationApplication.class, a);
  }
}

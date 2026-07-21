package com.yo.trace;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TraceApplication {
  public static void main(String[] a) {
    SpringApplication.run(TraceApplication.class, a);
  }
}

package com.yo.risk;

import com.yo.api.annotations.EnableYoFeignClients;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableYoFeignClients
public class RiskControlApplication {
  public static void main(String[] a) {
    SpringApplication.run(RiskControlApplication.class, a);
  }
}

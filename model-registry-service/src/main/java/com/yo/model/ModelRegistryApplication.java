package com.yo.model;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.yo.model.mapper")
@SpringBootApplication(scanBasePackages = "com.yo")
public class ModelRegistryApplication {
  public static void main(String[] args) {
    SpringApplication.run(ModelRegistryApplication.class, args);
  }
}

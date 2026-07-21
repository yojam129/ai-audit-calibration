package com.yo.integration.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
public class MinioConfig {
  @Bean
  MinioClient minioClient(
      @Value("${app.minio.endpoint}") String e,
      @Value("${app.minio.access-key}") String a,
      @Value("${app.minio.secret-key}") String s) {
    return MinioClient.builder().endpoint(e).credentials(a, s).build();
  }
}

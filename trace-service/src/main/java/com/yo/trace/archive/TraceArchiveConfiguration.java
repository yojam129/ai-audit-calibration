package com.yo.trace.archive;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "trace.archive.enabled", havingValue = "true")
public class TraceArchiveConfiguration {
  @Bean
  MinioClient traceArchiveMinioClient(
      @Value("${trace.archive.endpoint}") String endpoint,
      @Value("${trace.archive.access-key}") String accessKey,
      @Value("${trace.archive.secret-key}") String secretKey) {
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }
}

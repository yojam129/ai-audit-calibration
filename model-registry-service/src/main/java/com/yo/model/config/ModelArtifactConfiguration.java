package com.yo.model.config;

import com.yo.model.service.ModelArtifactVerifier;
import com.yo.model.service.impl.MinioModelArtifactVerifier;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelArtifactConfiguration {
  @Bean
  MinioClient minioClient(
      @Value("${minio.endpoint}") String endpoint,
      @Value("${minio.access-key}") String accessKey,
      @Value("${minio.secret-key}") String secretKey) {
    return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
  }

  @Bean
  ModelArtifactVerifier modelArtifactVerifier(MinioClient minio) {
    return new MinioModelArtifactVerifier(minio);
  }
}

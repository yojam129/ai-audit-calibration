package com.yo.integration.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MinioBucketInitializer implements ApplicationRunner {
  private final MinioClient client;
  private final String bucket;

  public MinioBucketInitializer(
      MinioClient client, @Value("${app.minio.incoming-bucket}") String bucket) {
    this.client = client;
    this.bucket = bucket;
  }

  @Override
  public void run(ApplicationArguments args) throws Exception {
    if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
      client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }
  }
}

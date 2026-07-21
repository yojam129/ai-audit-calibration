package com.yo.trace.archive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.trace.domain.po.TraceRecord;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "trace.archive.enabled", havingValue = "true")
public class AuditArchiveAdapter {
  private final MinioClient minio;
  private final ObjectMapper json;
  private final String bucket;

  public AuditArchiveAdapter(
      MinioClient minio, ObjectMapper json, @Value("${trace.archive.bucket}") String bucket) {
    this.minio = minio;
    this.json = json;
    this.bucket = bucket;
  }

  public String archive(List<TraceRecord> records) {
    try {
      if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
        minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      }
      byte[] content = json.writeValueAsBytes(records);
      String objectKey = "audit-manifest/" + LocalDate.now() + "/" + UUID.randomUUID() + ".json";
      minio.putObject(
          PutObjectArgs.builder()
              .bucket(bucket)
              .object(objectKey)
              .contentType("application/json")
              .stream(new ByteArrayInputStream(content), content.length, -1)
              .build());
      return objectKey;
    } catch (Exception e) {
      throw new IllegalStateException("Unable to archive audit manifest", e);
    }
  }
}

package com.yo.model.service.impl;

import com.yo.model.service.ModelArtifactVerifier;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;

public class MinioModelArtifactVerifier implements ModelArtifactVerifier {
  private final MinioClient minio;

  public MinioModelArtifactVerifier(MinioClient minio) {
    this.minio = minio;
  }

  @Override
  public void verify(String artifactUri, String expectedSha256) {
    try {
      URI uri = URI.create(artifactUri);
      if (!"minio".equals(uri.getScheme())) throw new IllegalArgumentException("Only minio:// artifacts are allowed");
      try (InputStream input =
          minio.getObject(
              GetObjectArgs.builder()
                  .bucket(uri.getHost())
                  .object(uri.getPath().substring(1))
                  .build())) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!MessageDigest.isEqual(actual.getBytes(), expectedSha256.toLowerCase().getBytes()))
          throw new IllegalArgumentException("Model artifact SHA-256 mismatch");
      }
    } catch (IllegalArgumentException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot verify model artifact", failure);
    }
  }
}

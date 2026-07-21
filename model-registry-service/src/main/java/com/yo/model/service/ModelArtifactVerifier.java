package com.yo.model.service;

public interface ModelArtifactVerifier {
  void verify(String artifactUri, String expectedSha256);
}

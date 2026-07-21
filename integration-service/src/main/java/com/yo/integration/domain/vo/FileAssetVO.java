package com.yo.integration.domain.vo;

import java.time.LocalDateTime;

public record FileAssetVO(
    Long id,
    String assetNo,
    String bucket,
    String objectKey,
    String originalName,
    long sizeBytes,
    String sha256,
    String status,
    LocalDateTime createdAt) {}

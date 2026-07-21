package com.yo.integration.domain.vo;

import java.time.Instant;

public record PresignVO(
    Long assetId,
    String assetNo,
    String bucket,
    String objectKey,
    String uploadUrl,
    Instant expiresAt) {}

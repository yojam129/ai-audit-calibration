package com.yo.api.client.review;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.REVIEW,
    contextId = "reviewClient",
    configuration = FeignInternalConfiguration.class)
public interface ReviewClient {
  @PostMapping("/api/v1/reviews")
  ReviewTaskVO create(@RequestBody CreateReviewRequest request);

  record CreateReviewRequest(UUID sampleId) {}

  record ReviewTaskVO(
      UUID id,
      UUID sampleId,
      String status,
      String assignee,
      long version,
      LocalDateTime createdAt) {}
}

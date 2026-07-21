package com.yo.api.client.learning;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.LEARNING,
    contextId = "learningClient",
    configuration = FeignInternalConfiguration.class)
public interface LearningClient {
  @PostMapping("/api/v1/learning")
  long assign(@RequestBody LearningRequest request);

  record LearningRequest(String reviewerId, String courseCode, String reason, String riskLevel) {}
}

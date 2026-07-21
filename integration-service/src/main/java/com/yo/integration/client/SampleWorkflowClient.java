package com.yo.integration.client;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import com.yo.common.domain.vo.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
    name = ServiceNames.SAMPLE,
    contextId = "sampleWorkflowClient",
    configuration = FeignInternalConfiguration.class)
public interface SampleWorkflowClient {
  @PostMapping("/api/samples/{id}/workflow/import-completed")
  ApiResponse<Void> importCompleted(@PathVariable("id") long sampleId);
}

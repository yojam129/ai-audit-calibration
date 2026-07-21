package com.yo.api.client.model;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import com.yo.common.domain.vo.ApiResponse;
import java.time.LocalDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.MODEL_REGISTRY,
    contextId = "modelRegistryClient",
    configuration = FeignInternalConfiguration.class)
public interface ModelRegistryClient {
  @GetMapping("/api/models/current")
  ApiResponse<ModelVersionVO> current(@RequestParam("modelCode") String modelCode);

  @PostMapping("/api/models/{id}/deployment")
  ApiResponse<ModelVersionVO> deploy(
      @PathVariable("id") long id, @RequestParam("trafficPercent") int trafficPercent);

  record ModelVersionVO(
      long id,
      String modelCode,
      String version,
      String runtime,
      String artifactUri,
      String checksum,
      String status,
      int trafficPercent,
      LocalDateTime createdAt) {}
}

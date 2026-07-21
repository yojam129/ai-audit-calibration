package com.yo.api.client.statistics;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
    name = ServiceNames.STATISTICS,
    contextId = "statisticsClient",
    configuration = FeignInternalConfiguration.class)
public interface StatisticsClient {
  @PostMapping("/internal/statistics/rebuild")
  RebuildResult rebuild();

  record RebuildResult(boolean rebuilt, long factCount) {}
}

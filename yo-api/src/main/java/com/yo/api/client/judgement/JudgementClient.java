package com.yo.api.client.judgement;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import java.util.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.JUDGEMENT,
    contextId = "judgementClient",
    configuration = FeignInternalConfiguration.class)
public interface JudgementClient {
  @PostMapping("/internal/judgements/comparisons")
  ComparisonVO compare(@RequestBody ComparisonRequest request);

  record TargetDecision(
      String targetCode,
      String systemLabel,
      String primaryLabel,
      String aiLabel,
      Double aiConfidence,
      boolean criticalTarget,
      boolean internalControl,
      boolean crossChannelRisk) {}

  record ComparisonRequest(
      UUID sampleId,
      long comparisonVersion,
      String primaryReviewerId,
      Long primaryAuthUserId,
      long primaryDurationMs,
      List<TargetDecision> targets) {}

  record ComparisonVO(
      UUID sampleId,
      long comparisonVersion,
      String consistency,
      int riskRank,
      List<String> reasonCodes,
      List<Object> targets) {}
}

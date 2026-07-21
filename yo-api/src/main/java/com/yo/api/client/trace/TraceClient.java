package com.yo.api.client.trace;

import com.yo.api.config.FeignInternalConfiguration;
import com.yo.api.constants.ServiceNames;
import java.time.LocalDateTime;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = ServiceNames.TRACE,
    contextId = "traceClient",
    configuration = FeignInternalConfiguration.class)
public interface TraceClient {
  @PostMapping("/api/v1/traces")
  TraceVO append(@RequestBody AppendTraceRequest request);

  record AppendTraceRequest(
      String aggregateType,
      String aggregateId,
      String action,
      String operatorId,
      String payloadJson) {}

  record TraceVO(
      long id,
      String aggregateType,
      String aggregateId,
      String action,
      String operatorId,
      String hash,
      String previousHash,
      LocalDateTime createdAt) {}
}

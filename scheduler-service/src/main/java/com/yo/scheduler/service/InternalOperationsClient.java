package com.yo.scheduler.service;

import com.yo.api.internal.InternalHmac;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InternalOperationsClient {
  private final RestClient.Builder client;
  private final String secret;

  public InternalOperationsClient(
      RestClient.Builder client, @Value("${internal.hmac.secret:}") String secret) {
    this.client = client;
    this.secret = secret;
  }

  public String post(String service, String path, String parameter, Duration timeout) {
    String body = parameter == null ? "" : parameter;
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    String signature =
        InternalHmac.sign("POST", path, timestamp, body.getBytes(StandardCharsets.UTF_8), secret);
    return client
        .build()
        .post()
        .uri("http://" + service + path)
        .header("X-Internal-Call", "scheduler-service")
        .header("X-Internal-Timestamp", timestamp)
        .header("X-Internal-Signature", signature)
        .header("X-Job-Timeout-Millis", String.valueOf(timeout.toMillis()))
        .body(body)
        .retrieve()
        .body(String.class);
  }
}

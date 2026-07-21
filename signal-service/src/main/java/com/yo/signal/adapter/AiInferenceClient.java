package com.yo.signal.adapter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiInferenceClient {
  private final RestClient client;

  public AiInferenceClient(
      RestClient.Builder builder,
      @Value("${app.ai-inference.base-url:http://localhost:18000}") String baseUrl,
      @Value("${app.ai-inference.timeout:3s}") Duration timeout) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(timeout);
    factory.setReadTimeout(timeout);
    client = builder.baseUrl(baseUrl).requestFactory(factory).build();
  }

  public Result infer(
      String subjectId, String channel, List<Double> rawValues, String modelVersion,
      Double ctValue, Double concentrationValue, String concentrationUnit,
      String riskLevel, List<String> riskFlags) {
    RuntimeException last = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        Result result =
            client
                .post()
                .uri("/v1/inference/curves")
                .body(new Request(subjectId, channel, rawValues, ctValue, concentrationValue,
                    concentrationUnit, riskLevel, riskFlags == null ? List.of() : riskFlags))
                .retrieve()
                .body(Result.class);
        if (result == null) throw new IllegalStateException("AI response is empty");
        return result;
      } catch (RuntimeException ex) {
        last = ex;
      }
    }
    throw last == null ? new IllegalStateException("AI inference failed") : last;
  }

  record Request(
      String sample_id, String channel, List<Double> values, Double ct_value,
      Double concentration_value, String concentration_unit, String risk_level,
      List<String> risk_flags) {}

  public record Result(
      String sample_id,
      String judgement,
      Double confidence,
      List<String> reason_codes,
      String inference_logic,
      Map<String, Double> features,
      String provider,
      String model_version,
      boolean degraded,
      List<String> degradation_reasons) {}
}

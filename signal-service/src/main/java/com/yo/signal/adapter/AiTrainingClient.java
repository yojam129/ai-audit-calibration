package com.yo.signal.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiTrainingClient {
  private final RestClient client;

  public AiTrainingClient(
      RestClient.Builder builder,
      @Value("${app.ai-training.base-url:${app.ai-inference.base-url:http://localhost:18000}}")
          String baseUrl,
      @Value("${app.ai-training.timeout:30m}") Duration timeout) {
    var requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(timeout);
    this.client = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  public TrainingResponse train(TrainingRequest request) {
    var response =
        client.post().uri("/v1/training/incremental").body(request).retrieve()
            .body(TrainingResponse.class);
    if (response == null) throw new IllegalStateException("AI training response is empty");
    return response;
  }

  @Data
  public static class TrainingRequest {
    private String trainingKey;
    private String modelCode;
    private String baseModelVersion;
    private boolean activate;
    private int trafficPercent;
    private List<TrainingSample> samples;
  }

  @Data
  public static class TrainingSample {
    private String feedbackKey;
    private String sampleId;
    private String runNo;
    private String curveId;
    private String chamber;
    private String channelCode;
    private String targetCode;
    private String aiLabel;
    private String truthLabel;
    private String sourceModelVersion;
    private List<Double> rawValues;
    private Double ctValue;
    private Double concentrationValue;
    private String concentrationUnit;
    private String riskLevel;
    private List<String> riskFlags;
    private String curveChecksum;
  }

  @Data
  public static class TrainingResponse {
    private String status;
    @JsonProperty("training_key")
    private String trainingKey;
    @JsonProperty("model_version")
    private String modelVersion;
    @JsonProperty("model_id")
    private Long modelId;
    @JsonProperty("sample_count")
    private int sampleCount;
    private String detail;
  }
}

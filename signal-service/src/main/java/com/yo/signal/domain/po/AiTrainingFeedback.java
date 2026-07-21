package com.yo.signal.domain.po;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document("ai_training_feedback")
@CompoundIndex(name = "uk_feedback_key", def = "{'feedbackKey':1}", unique = true)
public class AiTrainingFeedback {
  @Id private String id;
  private String feedbackKey;
  private String eventId;
  private String sampleId;
  private long truthVersion;
  private String runNo;
  private String curveId;
  private String chamber;
  private String channelCode;
  private String targetCode;
  private String aiLabel;
  private String truthLabel;
  private String sourceModelVersion;
  private List<Double> rawValues;
  private List<Double> correctedValues;
  private Double ctValue;
  private Double concentrationValue;
  private String concentrationUnit;
  private String riskLevel;
  private List<String> riskFlags;
  private Map<String, Double> features;
  private String curveChecksum;
  private Instant confirmedAt;
  private String status;
  private String trainingKey;
  private String trainedModelVersion;
  private Instant createdAt;
  private Instant trainedAt;
}

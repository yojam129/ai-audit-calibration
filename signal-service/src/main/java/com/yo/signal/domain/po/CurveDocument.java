package com.yo.signal.domain.po;

import java.time.*;
import java.util.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Document("fluorescence_curve")
@Data
@CompoundIndex(
    name = "uk_curve_version",
    def = "{'runNo':1,'chamber':1,'channelCode':1,'processingVersion':1}",
    unique = true)
public class CurveDocument {
  @Id public String id;
  public String runNo;
  public String chamber;
  public String channelCode;
  public String targetCode;
  public String processingVersion;
  public List<Double> rawValues;
  public List<Double> correctedValues;
  public Double ctValue;
  public Double concentrationValue;
  public String concentrationUnit;
  public String riskLevel;
  public List<String> riskFlags;
  public Map<String, Double> features;
  public String qcStatus;
  public String checksum;
  public Instant createdAt;
}

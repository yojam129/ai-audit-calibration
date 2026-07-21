package com.yo.risk.domain.vo;

import java.time.Instant;
import lombok.Data;

@Data
public class ReviewerErrorFocusVO {
  private Long id;
  private String reviewerId;
  private String sampleId;
  private String sampleNo;
  private String chamber;
  private String channelCode;
  private String targetCode;
  private String predictedLabel;
  private String truthLabel;
  private String errorType;
  private Instant occurredAt;
}

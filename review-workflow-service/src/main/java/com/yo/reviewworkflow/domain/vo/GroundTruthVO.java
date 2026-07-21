package com.yo.reviewworkflow.domain.vo;

import com.yo.reviewworkflow.enums.ReviewEnums.TruthLabel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class GroundTruthVO {
  private UUID id;
  private UUID sampleId;
  private String sampleNo;
  private long truthVersion;
  private UUID taskId;
  private String reviewerId;
  private Long authUserId;
  private Long durationMs;
  private Instant confirmedAt;
  private List<Target> targets;

  @Data
  public static class Target {
    private String targetCode;
    private TruthLabel truthLabel;
    private String reasonCode;
    private String remark;
    private TruthLabel systemLabel;
    private TruthLabel primaryLabel;
    private TruthLabel aiLabel;
    private Boolean systemCorrect;
    private Boolean primaryCorrect;
    private Boolean aiCorrect;
  }
}

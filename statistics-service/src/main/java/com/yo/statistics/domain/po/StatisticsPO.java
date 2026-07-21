package com.yo.statistics.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import java.util.*;
import lombok.Data;

public final class StatisticsPO {
  private StatisticsPO() {}

  @TableName("accuracy_projection")
  @Data
  public static class Accuracy {
    @TableId public String sourceType;
    public long correctCount;
    public long totalCount;
    public Instant updatedAt;
  }

  @TableName("confusion_projection")
  @Data
  public static class Confusion {
    @TableId(type = IdType.INPUT)
    public String projectionKey;

    public String sourceType;
    public String targetCode;
    public long tp;
    public long tn;
    public long fp;
    public long fn;
    public long indeterminate;
    public long invalidCount;
    public Instant updatedAt;
  }

  @TableName("statistics_consumed_event")
  @Data
  public static class Event {
    @TableId public UUID eventId;
    public Instant consumedAt;
  }

  @TableName("daily_accuracy_projection")
  @Data
  public static class DailyAccuracy {
    @TableId public String projectionKey;
    public LocalDate metricDate;
    public String sourceType;
    public long correctCount;
    public long totalCount;
    public Instant updatedAt;
  }

  @TableName("ground_truth_outcome_fact")
  @Data
  public static class OutcomeFact {
    @TableId(type = IdType.AUTO)
    public Long id;

    public UUID eventId;
    public UUID sampleId;
    public long truthVersion;
    public String targetCode;
    public String sourceType;
    public String instrumentConclusion;
    public String aiConclusion;
    public String humanConclusion;
    public String truthLabel;
    public String reviewerId;
    public Long authUserId;
    public long durationMs;
    public boolean archived;
    public boolean secondaryTruthConfirmed;
    public Instant archivedAt;
    public Instant occurredAt;
    public Instant createdAt;
  }
}

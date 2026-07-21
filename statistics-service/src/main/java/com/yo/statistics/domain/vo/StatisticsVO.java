package com.yo.statistics.domain.vo;

import java.util.*;
import java.time.Instant;
import lombok.Data;

public final class StatisticsVO {
  private StatisticsVO() {}

  public record Accuracy(String sourceType, long correct, long total, double rate) {}

  public record Confusion(
      String sourceType,
      String targetCode,
      long tp,
      long tn,
      long fp,
      long fn,
      long indeterminate,
      long invalid,
      double sensitivity,
      double specificity) {}

  public record Dashboard(
      List<Accuracy> accuracy, List<Confusion> confusion, long finalizedSamples) {}

  public record TrendPoint(
      java.time.LocalDate date, String sourceType, long correct, long total, double rate) {}

  @Data
  public static class InconsistencyDetail {
    private String sampleId;
    private long truthVersion;
    private String targetCode;
    private String truthLabel;
    private String systemLabel;
    private String primaryLabel;
    private String aiLabel;
    private Boolean systemCorrect;
    private Boolean primaryCorrect;
    private Boolean aiCorrect;
    private Instant occurredAt;
  }
}

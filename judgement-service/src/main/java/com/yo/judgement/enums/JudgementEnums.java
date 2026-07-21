package com.yo.judgement.enums;

public final class JudgementEnums {
  private JudgementEnums() {}

  public enum Label {
    POSITIVE,
    NEGATIVE,
    INDETERMINATE,
    INVALID
  }

  public enum Source {
    SYSTEM,
    PRIMARY,
    AI
  }

  public enum Consistency {
    ALL_AGREE,
    TWO_AGREE_ONE_DIFF,
    ALL_DIFFERENT,
    UNCERTAIN
  }
}

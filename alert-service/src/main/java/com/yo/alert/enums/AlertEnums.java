package com.yo.alert.enums;

public final class AlertEnums {
  private AlertEnums() {}

  public enum Level {
    P1,
    P2,
    P3
  }

  public enum Status {
    OPEN,
    CLAIMED,
    ESCALATED,
    RESOLVED
  }
}

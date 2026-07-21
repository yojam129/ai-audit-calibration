package com.yo.model.enums;

import java.util.Set;

public enum ModelStatus {
  REGISTERED,
  VALIDATED,
  CANARY,
  ACTIVE,
  INACTIVE,
  REJECTED,
  ROLLED_BACK;

  public boolean canTransitionTo(ModelStatus target) {
    return switch (this) {
      case REGISTERED -> Set.of(VALIDATED, REJECTED).contains(target);
      case VALIDATED -> Set.of(CANARY, ACTIVE, REJECTED).contains(target);
      case CANARY -> Set.of(ACTIVE, INACTIVE, ROLLED_BACK).contains(target);
      case ACTIVE -> Set.of(INACTIVE, ROLLED_BACK).contains(target);
      case INACTIVE -> Set.of(CANARY, ACTIVE).contains(target);
      case REJECTED, ROLLED_BACK -> false;
    };
  }
}

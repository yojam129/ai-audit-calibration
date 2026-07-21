package com.yo.judgement;

import static org.assertj.core.api.Assertions.*;

import com.yo.judgement.enums.JudgementEnums.*;
import org.junit.jupiter.api.*;

class ComparisonTest {
  @Test
  void fourStatesExist() {
    assertThat(Label.values())
        .containsExactly(Label.POSITIVE, Label.NEGATIVE, Label.INDETERMINATE, Label.INVALID);
    assertThat(Consistency.values()).hasSize(4);
  }
}

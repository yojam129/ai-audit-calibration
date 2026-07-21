package com.yo.statistics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yo.statistics.domain.po.StatisticsPO.OutcomeFact;
import org.junit.jupiter.api.Test;

class OutcomeFactTest {
  @Test
  void retainsAllThreeSourceConclusionsForAuditAndRebuild() {
    OutcomeFact fact = new OutcomeFact();
    fact.sourceType = "AI";
    fact.instrumentConclusion = "NEGATIVE";
    fact.aiConclusion = "POSITIVE";
    fact.humanConclusion = "SUSPICIOUS";
    fact.truthLabel = "POSITIVE";
    assertEquals("NEGATIVE", fact.instrumentConclusion);
    assertEquals("POSITIVE", fact.aiConclusion);
    assertEquals("SUSPICIOUS", fact.humanConclusion);
    assertEquals("POSITIVE", fact.truthLabel);
  }
}

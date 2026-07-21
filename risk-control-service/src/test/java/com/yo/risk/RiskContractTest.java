package com.yo.risk;

import static org.junit.jupiter.api.Assertions.*;

import com.yo.risk.domain.vo.RiskMetricVO;
import com.yo.risk.domain.dto.ReviewOutcomeDTO;
import com.yo.risk.enums.RiskLevel;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RiskContractTest {
  @Test
  void carriesEvidence() {
    assertEquals(
        1,
        new RiskMetricVO(
                "u", null, 10, 9, .9, 50, 45, .9, true, 100,
                Map.of("MISREAD", 1L), RiskLevel.WATCH, false)
            .errorCounts()
            .get("MISREAD"));
  }

  @Test
  void reviewerOutcomeCarriesExplicitIdentityAndEventTime() {
    Instant occurredAt = Instant.parse("2026-07-19T01:02:03Z");
    var event = new ReviewOutcomeDTO("event-1", "reviewer-1", 42L, false, 1234, "MISREAD", occurredAt);
    assertEquals(42L, event.authUserId());
    assertEquals(1234, event.durationMs());
    assertEquals(occurredAt, event.occurredAt());
  }
}

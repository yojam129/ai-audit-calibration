package com.yo.reviewworkflow.service;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReviewRoutingPolicy {
  private final int samplingPercent;

  public ReviewRoutingPolicy(@Value("${review.sampling-percent:0}") int samplingPercent) {
    this.samplingPercent = Math.max(0, Math.min(100, samplingPercent));
  }

  public Route route(UUID sampleId, String consistency, int riskRank) {
    boolean sampling =
        "ALL_AGREE".equals(consistency)
            && Math.floorMod(sampleId.hashCode(), 100) < samplingPercent;
    if ("ALL_AGREE".equals(consistency)) return new Route("P3", sampling, !sampling);
    if ("ALL_DIFFERENT".equals(consistency) || riskRank >= 4) return new Route("P1", false, false);
    return new Route("P2", false, false);
  }

  public record Route(String priority, boolean sampling, boolean autoArchive) {}
}

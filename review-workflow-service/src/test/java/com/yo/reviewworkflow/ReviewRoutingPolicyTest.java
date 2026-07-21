package com.yo.reviewworkflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.yo.reviewworkflow.service.ReviewRoutingPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewRoutingPolicyTest {
  private static final UUID SAMPLE = UUID.fromString("10000000-0000-0000-0000-000000000001");

  @Test
  void allAgreeArchivesWhenSamplingDisabled() {
    var route = new ReviewRoutingPolicy(0).route(SAMPLE, "ALL_AGREE", 0);
    assertThat(route.autoArchive()).isTrue();
    assertThat(route.sampling()).isFalse();
  }

  @Test
  void allAgreeCanBeForcedIntoSampling() {
    var route = new ReviewRoutingPolicy(100).route(SAMPLE, "ALL_AGREE", 0);
    assertThat(route.autoArchive()).isFalse();
    assertThat(route.sampling()).isTrue();
  }

  @Test
  void twoAgainstOneRoutesToP2Review() {
    var route = new ReviewRoutingPolicy(0).route(SAMPLE, "TWO_AGREE_ONE_DIFF", 2);
    assertThat(route.priority()).isEqualTo("P2");
    assertThat(route.autoArchive()).isFalse();
  }

  @Test
  void allDifferentRoutesToP1() {
    var route = new ReviewRoutingPolicy(0).route(SAMPLE, "ALL_DIFFERENT", 4);
    assertThat(route.priority()).isEqualTo("P1");
    assertThat(route.autoArchive()).isFalse();
  }
}

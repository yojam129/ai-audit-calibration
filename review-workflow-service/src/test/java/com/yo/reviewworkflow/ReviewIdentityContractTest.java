package com.yo.reviewworkflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo.reviewworkflow.domain.po.GroundTruthPO;
import com.yo.reviewworkflow.domain.po.ReviewTaskPO;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReviewIdentityContractTest {
  @Test
  void persistsAuthenticatedIdentitySeparatelyFromDomainReviewer() {
    assertTrue(
        Arrays.stream(ReviewTaskPO.class.getFields())
            .anyMatch(field -> field.getName().equals("ownerAuthUserId")));
    assertTrue(
        Arrays.stream(GroundTruthPO.class.getFields())
            .anyMatch(field -> field.getName().equals("authUserId")));
    assertTrue(
        Arrays.stream(GroundTruthPO.class.getFields())
            .anyMatch(field -> field.getName().equals("durationMs")));
  }
}

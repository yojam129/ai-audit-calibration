package com.yo.learning;

import static org.junit.jupiter.api.Assertions.*;

import com.yo.learning.domain.vo.PermissionRestoreApplicationVO;
import org.junit.jupiter.api.Test;

class LearningContractTest {
  @Test
  void exposesScore() {
    assertEquals(
        88,
        new PermissionRestoreApplicationVO(
                1,
                "u",
                "c",
                "error",
                null,
                null,
                null,
                null,
                null,
                88,
                "RESTORED",
                null,
                "process-1",
                null)
            .bestScore());
  }
}

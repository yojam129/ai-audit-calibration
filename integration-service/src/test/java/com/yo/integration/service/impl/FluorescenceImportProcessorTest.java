package com.yo.integration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FluorescenceImportProcessorTest {
  @Test
  void normalizesTemplateHeaderAliases() {
    assertEquals("A腔室FAM通道CT值", FluorescenceImportProcessor.normalizeHeader(" A腔室 FAM通道Ct值 "));
  }

  @Test
  void parsesExcelDateTimeText() {
    assertEquals(
        LocalDateTime.of(2026, 4, 16, 13, 38, 16),
        FluorescenceImportProcessor.parseTime("2026/4/16 13:38:16"));
  }

  @Test
  void rejectsInvalidDateTime() {
    assertThrows(
        IllegalArgumentException.class, () -> FluorescenceImportProcessor.parseTime("not-a-date"));
  }
}

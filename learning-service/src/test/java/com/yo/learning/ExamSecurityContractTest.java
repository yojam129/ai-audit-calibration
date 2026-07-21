package com.yo.learning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo.learning.domain.dto.ExamDTO;
import com.yo.learning.domain.vo.ExamVO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExamSecurityContractTest {
  @Test
  void questionViewNeverContainsCorrectAnswer() {
    assertFalse(
        List.of(ExamVO.QuestionVO.class.getRecordComponents()).stream()
            .anyMatch(component -> component.getName().toLowerCase().contains("correct")));
  }

  @Test
  void submissionContainsAnswersInsteadOfClientScore() {
    var names =
        List.of(ExamDTO.class.getRecordComponents()).stream()
            .map(component -> component.getName())
            .toList();
    assertTrue(names.contains("answers"));
    assertFalse(names.contains("score"));
  }
}

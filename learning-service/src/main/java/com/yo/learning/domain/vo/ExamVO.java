package com.yo.learning.domain.vo;
import java.util.List;
public record ExamVO(long attemptId, List<QuestionVO> questions) {
  public record QuestionVO(long id, String stem, List<String> options, int score) {}
}

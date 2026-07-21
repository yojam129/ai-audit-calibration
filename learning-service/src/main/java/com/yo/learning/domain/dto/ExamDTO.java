package com.yo.learning.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ExamDTO(
    @NotNull Long attemptId, @NotEmpty List<@Valid AnswerDTO> answers) {
  public record AnswerDTO(@NotNull Long questionId, @NotEmpty List<String> selectedOptions) {}
}

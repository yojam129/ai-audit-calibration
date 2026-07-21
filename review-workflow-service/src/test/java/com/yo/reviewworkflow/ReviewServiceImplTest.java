package com.yo.reviewworkflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.sample.SampleClient;
import com.yo.common.domain.vo.ApiResponse;
import com.yo.reviewworkflow.domain.dto.ReviewDTO;
import com.yo.reviewworkflow.domain.po.ReviewTaskPO;
import com.yo.reviewworkflow.infrastructure.FlowableRestClient;
import com.yo.reviewworkflow.mapper.ReviewMappers.Outbox;
import com.yo.reviewworkflow.mapper.ReviewMappers.Task;
import com.yo.reviewworkflow.mapper.ReviewMappers.Truth;
import com.yo.reviewworkflow.service.impl.ReviewServiceImpl;
import com.yo.reviewworkflow.domain.po.GroundTruthPO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yo.security.context.CurrentUser;
import com.yo.security.context.CurrentUserContext;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ReviewServiceImplTest {
  private static final UUID SAMPLE_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID TASK_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000001");

  private Task tasks;
  private Truth truths;
  private SampleClient samples;
  private ReviewServiceImpl service;

  @BeforeEach
  void setUp() {
    tasks = mock(Task.class);
    truths = mock(Truth.class);
    samples = mock(SampleClient.class);
    service = new ReviewServiceImpl(
        tasks,
        truths,
        mock(Outbox.class),
        mock(FlowableRestClient.class),
        samples,
        mock(RabbitTemplate.class),
        new ObjectMapper());
    CurrentUserContext.set(new CurrentUser(
        9L, 1L, "secondary", Set.of("SECONDARY_REVIEWER"), Set.of("review:handle")));
  }

  @AfterEach
  void tearDown() {
    CurrentUserContext.clear();
  }

  @Test
  void detailResolvesRealSampleNumberByBusinessId() {
    ReviewTaskPO task = task("P2", "PENDING");
    when(tasks.selectById(TASK_ID)).thenReturn(task);
    when(samples.getByBusinessId(SAMPLE_ID)).thenReturn(ApiResponse.ok(
        new SampleClient.SampleVO(
            1L, "SAMPLE-20260720-001", "ORG-1", "DETECTED", "PCR", LocalDateTime.now())));

    assertThat(service.detail(TASK_ID).sampleNo()).isEqualTo("SAMPLE-20260720-001");
  }

  @Test
  void mandatoryIsTrueWhenAnyUnfinishedP1Exists() {
    when(tasks.selectCount(any())).thenReturn(1L);

    assertThat(service.hasMandatoryReview()).isTrue();
  }

  @Test
  void allDifferentCannotBeCreatedBelowEscalatedP1() {
    when(samples.getByBusinessId(SAMPLE_ID)).thenReturn(ApiResponse.ok(
        new SampleClient.SampleVO(
            1L, "SAMPLE-20260720-001", "ORG-1", "DETECTED", "PCR", LocalDateTime.now())));

    var result = service.create(new ReviewDTO.Create(
        SAMPLE_ID, "primary-1", 1L, 1000L, "P3", "ALL_DIFFERENT", false, List.of()));

    assertThat(result.priority()).isEqualTo("P1");
    assertThat(result.status())
        .isEqualTo(com.yo.reviewworkflow.enums.ReviewEnums.Status.ESCALATED);
  }

  @Test
  void targetRiskRankFourCannotBeCreatedBelowEscalatedP1() {
    when(samples.getByBusinessId(SAMPLE_ID)).thenReturn(ApiResponse.ok(
        new SampleClient.SampleVO(
            1L, "SAMPLE-20260720-001", "ORG-1", "DETECTED", "PCR", LocalDateTime.now())));
    var target = new ReviewDTO.SourceTarget(
        "RSV", null, null, null, "TWO_AGREE_ONE_DIFF", "AI", 4, List.of("CRITICAL"));

    var result = service.create(new ReviewDTO.Create(
        SAMPLE_ID, "primary-1", 1L, 1000L, "P3", "TWO_AGREE_ONE_DIFF", false,
        List.of(target)));

    assertThat(result.priority()).isEqualTo("P1");
    assertThat(result.status())
        .isEqualTo(com.yo.reviewworkflow.enums.ReviewEnums.Status.ESCALATED);
  }

  @Test
  void p1CannotBeFinalizedAsNonHighRisk() {
    when(tasks.selectById(TASK_ID)).thenReturn(task("P1", "IN_REVIEW"));
    ReviewDTO.Finalize request = new ReviewDTO.Finalize(
        "secondary-9",
        0,
        List.of(new ReviewDTO.TruthTarget(
            "RSV", com.yo.reviewworkflow.enums.ReviewEnums.TruthLabel.NEGATIVE,
            "NO_AMPLIFICATION", null, null, null, null)),
        false,
        "signed");

    assertThatThrownBy(() -> service.finalizeTask(TASK_ID, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("P1 review must remain high risk");
  }

  @Test
  void p1RequiresElectronicSignature() {
    when(tasks.selectById(TASK_ID)).thenReturn(task("P1", "IN_REVIEW"));
    ReviewDTO.Finalize request = new ReviewDTO.Finalize(
        "secondary-9",
        0,
        List.of(new ReviewDTO.TruthTarget(
            "RSV", com.yo.reviewworkflow.enums.ReviewEnums.TruthLabel.NEGATIVE,
            "NO_AMPLIFICATION", null, null, null, null)),
        true,
        " ");

    assertThatThrownBy(() -> service.finalizeTask(TASK_ID, request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("signature required");
  }

  @Test
  void truthPageReturnsFinalLabelAndReverseAccuracyForEverySource() throws Exception {
    var truth = new GroundTruthPO();
    truth.id = UUID.randomUUID();
    truth.sampleId = SAMPLE_ID;
    truth.taskId = TASK_ID;
    truth.truthVersion = 1;
    truth.reviewerId = "secondary-9";
    truth.confirmedAt = java.time.Instant.now();
    truth.targetsJson = new ObjectMapper().writeValueAsString(List.of(
        new ReviewDTO.TruthTarget(
            "RSV",
            com.yo.reviewworkflow.enums.ReviewEnums.TruthLabel.POSITIVE,
            "CURVE_CONFIRMED",
            "confirmed",
            com.yo.reviewworkflow.enums.ReviewEnums.TruthLabel.NEGATIVE,
            com.yo.reviewworkflow.enums.ReviewEnums.TruthLabel.POSITIVE,
            com.yo.reviewworkflow.enums.ReviewEnums.TruthLabel.INDETERMINATE)));
    var page = new Page<GroundTruthPO>(1, 20, 1);
    page.setRecords(List.of(truth));
    when(truths.selectPage(any(), any())).thenReturn(page);
    when(samples.getByBusinessId(SAMPLE_ID)).thenReturn(ApiResponse.ok(
        new SampleClient.SampleVO(
            1L, "SAMPLE-20260720-001", "ORG-1", "DETECTED", "PCR", LocalDateTime.now())));

    var result = service.truthPage(1, 20, null);

    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getRecords().getFirst().getSampleNo()).isEqualTo("SAMPLE-20260720-001");
    var target = result.getRecords().getFirst().getTargets().getFirst();
    assertThat(target.getTruthLabel().name()).isEqualTo("POSITIVE");
    assertThat(target.getSystemCorrect()).isFalse();
    assertThat(target.getPrimaryCorrect()).isTrue();
    assertThat(target.getAiCorrect()).isFalse();
  }

  private ReviewTaskPO task(String priority, String status) {
    ReviewTaskPO task = new ReviewTaskPO();
    task.id = TASK_ID;
    task.sampleId = SAMPLE_ID;
    task.priority = priority;
    task.status = status;
    task.ownerAuthUserId = 9L;
    task.version = 0;
    return task;
  }
}

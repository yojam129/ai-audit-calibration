package com.yo.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yo.learning.domain.dto.*;
import com.yo.learning.domain.po.*;
import com.yo.learning.domain.vo.*;
import com.yo.learning.mapper.*;
import com.yo.learning.service.*;
import com.yo.learning.infrastructure.LearningFlowableClient;
import com.yo.api.client.auth.AuthPermissionClient;
import com.yo.api.client.risk.RiskClient;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.security.context.CurrentUserContext;

@Service
public class LearningServiceImpl implements LearningService {
  private final LearningMapper mapper;
  private final ExamQuestionMapper questions;
  private final ExamAttemptMapper attempts;
  private final ExamAnswerMapper answers;
  private final LearningOutboxMapper outbox;
  private final ObjectMapper json;
  private final LearningFlowableClient flowable;
  private final AuthPermissionClient auth;
  private final RiskClient risk;
  private final TransactionTemplate transactions;

  public LearningServiceImpl(
      LearningMapper m,
      ExamQuestionMapper questions,
      ExamAttemptMapper attempts,
      ExamAnswerMapper answers,
      LearningOutboxMapper outbox,
      ObjectMapper json,
      LearningFlowableClient flowable,
      AuthPermissionClient auth,
      RiskClient risk,
      PlatformTransactionManager transactionManager) {
    mapper = m;
    this.questions = questions;
    this.attempts = attempts;
    this.answers = answers;
    this.outbox = outbox;
    this.json = json;
    this.flowable = flowable;
    this.auth = auth;
    this.risk = risk;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Transactional
  public long assign(LearningDTO x) {
    var a = new LearningAssignment();
    a.reviewerId = x.reviewerId();
    a.authUserId = x.authUserId();
    a.courseCode = x.courseCode();
    a.errorType = x.errorType();
    a.status = "ASSIGNED";
    a.workflowToken = UUID.randomUUID().toString();
    a.dueAt = Instant.now().plus(x.dueDays(), ChronoUnit.DAYS);
    mapper.insert(a);
    enqueuePermissionFreeze(a);
    enqueueWorkflowStart(a);
    return a.id;
  }

  public PermissionRestoreApplicationVO exam(long id, ExamDTO x) {
    LearningAssignment a = get(id);
    requireOwnerOrManager(a);
    requireWorkflowStarted(a);
    ExamOutcome outcome =
        transactions.execute(
            ignored -> gradeExam(id, x));
    if (outcome == null) throw new IllegalStateException("exam grading failed");
    flowable.completeCurrentTask(
        a.processInstanceId,
        LearningFlowableClient.EXAM_TASK,
        Map.of("allCorrect", outcome.allCorrect(), "examScore", outcome.score()));
    return vo(get(id));
  }

  @Transactional
  public ExamVO startExam(long assignmentId) {
    LearningAssignment assignment = get(assignmentId);
    requireOwnerOrManager(assignment);
    if (!"EXAM_REQUIRED".equals(assignment.status))
      throw new IllegalStateException("training must be completed before exam");
    requireWorkflowStarted(assignment);
    LearningFlowableClient.TaskInfo currentTask =
        flowable.queryCurrentTask(assignment.processInstanceId);
    if (currentTask == null
        || !LearningFlowableClient.EXAM_TASK.equals(currentTask.taskDefinitionKey()))
      throw new IllegalStateException("Flowable exam task is not active");
    List<ExamQuestion> list =
        questions.selectList(
            new QueryWrapper<ExamQuestion>()
                .eq("course_code", assignment.courseCode)
                .eq("enabled", true)
                .orderByAsc("id"));
    if (list.isEmpty()) throw new IllegalStateException("course has no exam questions");
    ExamAttempt attempt = new ExamAttempt();
    attempt.assignmentId = assignment.id;
    attempt.reviewerId = assignment.reviewerId;
    attempt.status = "STARTED";
    attempt.startedAt = Instant.now();
    attempts.insert(attempt);
    return new ExamVO(
        attempt.id,
        list.stream()
            .map(q -> new ExamVO.QuestionVO(q.id, q.stem, readOptions(q.optionsJson), q.score))
            .toList());
  }

  public PermissionRestoreApplicationVO completeTraining(long assignmentId) {
    LearningAssignment assignment = get(assignmentId);
    requireOwnerOrManager(assignment);
    if (!"ASSIGNED".equals(assignment.status) && !"LEARNING_REQUIRED".equals(assignment.status))
      throw new IllegalStateException("training is not pending");
    requireWorkflowStarted(assignment);
    flowable.completeCurrentTask(
        assignment.processInstanceId, LearningFlowableClient.TRAINING_TASK, Map.of());
    return vo(get(assignmentId));
  }

  @Override
  @Transactional
  public void markExamRequired(long id, String workflowToken) {
    LearningAssignment assignment = requireWorkflowCallback(id, workflowToken);
    if ("EXAM_REQUIRED".equals(assignment.status)) return;
    if (!"ASSIGNED".equals(assignment.status) && !"LEARNING_REQUIRED".equals(assignment.status))
      throw new IllegalStateException("training workflow is not waiting for exam");
    assignment.status = "EXAM_REQUIRED";
    mapper.updateById(assignment);
  }

  @Override
  @Transactional
  public void markRestorePending(long id, String workflowToken) {
    LearningAssignment assignment = requireWorkflowCallback(id, workflowToken);
    if ("RESTORE_PENDING".equals(assignment.status) || "RESTORED".equals(assignment.status)) return;
    if (!"EXAM_REQUIRED".equals(assignment.status))
      throw new IllegalStateException("exam workflow is not ready to restore permission");
    assignment.status = "RESTORE_PENDING";
    mapper.updateById(assignment);
  }

  @Override
  public void restorePermission(long id, String workflowToken) {
    LearningAssignment assignment = requireWorkflowCallback(id, workflowToken);
    if ("RESTORED".equals(assignment.status)) return;
    if (!"RESTORE_PENDING".equals(assignment.status))
      throw new IllegalStateException("permission restore is not pending");
    String operationId = "flowable-qualification-restore:" + assignment.id;
    auth.restore(
        new AuthPermissionClient.PermissionChange(
            operationId,
            assignment.authUserId,
            "judgement:submit",
            "Flowable training completed and exam passed with full marks",
            null));
    risk.resetQualificationWindow(assignment.reviewerId);
    transactions.executeWithoutResult(ignored -> markRestored(assignment.id, operationId));
  }

  private void requireOwnerOrManager(LearningAssignment assignment) {
    var current = CurrentUserContext.required();
    if (!current.userId().equals(assignment.authUserId)
        && !current.hasRole("SUPER_ADMIN")
        && !current.hasRole("QUALITY_MANAGER")) throw new SecurityException("access denied");
  }

  private List<String> readOptions(String value) {
    try {
      return json.readValue(value, new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException("invalid question options", e);
    }
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public IPage<PermissionRestoreApplicationVO> page(
      long current, long size, String reviewerId, String status) {
    var currentUser = CurrentUserContext.required();
    boolean manager = currentUser.hasRole("SUPER_ADMIN") || currentUser.hasRole("QUALITY_MANAGER");
    if (!manager) reviewerId = currentUser.username();
    var q = new QueryWrapper<LearningAssignment>().orderByDesc("id");
    if (reviewerId != null && !reviewerId.isBlank()) q.eq("reviewer_id", reviewerId);
    if (status != null && !status.isBlank()) q.eq("status", status);
    return mapper
        .selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), q)
        .convert(this::vo);
  }

  private LearningAssignment get(long id) {
    return Optional.ofNullable(mapper.selectById(id)).orElseThrow();
  }

  private PermissionRestoreApplicationVO vo(LearningAssignment a) {
    return new PermissionRestoreApplicationVO(
        a.id,
        a.reviewerId,
        a.courseCode,
        a.errorType,
        a.focusSampleId,
        a.focusSampleNo,
        a.focusChamber,
        a.focusChannelCode,
        a.focusTargetCode,
        a.bestScore,
        a.status,
        a.appliedAt,
        a.processInstanceId,
        a.workflowStartedAt);
  }

  private ExamOutcome gradeExam(long id, ExamDTO submittedExam) {
    LearningAssignment assignment = get(id);
    if (!"EXAM_REQUIRED".equals(assignment.status))
      throw new IllegalStateException("training must be completed before exam");
    ExamAttempt attempt =
        Optional.ofNullable(attempts.selectById(submittedExam.attemptId())).orElseThrow();
    if (!attempt.assignmentId.equals(id) || !"STARTED".equals(attempt.status))
      throw new IllegalStateException("invalid or submitted attempt");
    Map<Long, ExamDTO.AnswerDTO> submitted = new HashMap<>();
    submittedExam.answers().forEach(answer -> submitted.put(answer.questionId(), answer));
    List<ExamQuestion> examQuestions =
        questions.selectList(
            new QueryWrapper<ExamQuestion>()
                .eq("course_code", assignment.courseCode)
                .eq("enabled", true)
                .orderByAsc("id"));
    int earned = 0;
    int total = examQuestions.stream().mapToInt(question -> question.score).sum();
    for (ExamQuestion question : examQuestions) {
      ExamDTO.AnswerDTO submittedAnswer = submitted.get(question.id);
      Set<String> selected =
          submittedAnswer == null
              ? Set.of()
              : new TreeSet<>(submittedAnswer.selectedOptions());
      boolean correct = selected.equals(new TreeSet<>(readOptions(question.correctOptionsJson)));
      ExamAnswer saved = new ExamAnswer();
      saved.attemptId = attempt.id;
      saved.questionId = question.id;
      saved.selectedOptionsJson = write(selected);
      saved.correct = correct;
      saved.awardedScore = correct ? question.score : 0;
      answers.insert(saved);
      earned += saved.awardedScore;
    }
    double score = total == 0 ? 0 : earned * 100d / total;
    boolean allCorrect = !examQuestions.isEmpty() && earned == total;
    attempt.score = score;
    attempt.status = allCorrect ? "PASSED" : "FAILED";
    attempt.submittedAt = Instant.now();
    attempts.updateById(attempt);
    assignment.attempts++;
    assignment.bestScore = Math.max(assignment.bestScore, score);
    mapper.updateById(assignment);
    return new ExamOutcome(allCorrect, score);
  }

  private LearningAssignment requireWorkflowCallback(long id, String workflowToken) {
    LearningAssignment assignment = get(id);
    if (workflowToken == null
        || !workflowToken.equals(assignment.workflowToken)
        || assignment.processInstanceId == null) throw new SecurityException("invalid workflow callback");
    return assignment;
  }

  private void requireWorkflowStarted(LearningAssignment assignment) {
    if (assignment.processInstanceId == null || assignment.processInstanceId.isBlank())
      throw new IllegalStateException("Flowable workflow has not started yet");
  }

  private void enqueuePermissionFreeze(LearningAssignment assignment) {
    if (assignment.authUserId == null)
      throw new IllegalStateException("assignment has no explicit authUserId mapping");
    LearningOutbox event = new LearningOutbox();
    event.eventId = "assignment-freeze:" + assignment.id;
    event.eventType = "AUTH_PERMISSION_FREEZE";
    event.aggregateId = String.valueOf(assignment.id);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("authUserId", assignment.authUserId);
    payload.put("permissionCode", "judgement:submit");
    payload.put("reason", "accuracy below qualification threshold; Flowable training required");
    payload.put("approvedByAuthUserId", null);
    event.payloadJson = write(payload);
    event.status = "PENDING";
    event.nextAttemptAt = Instant.now();
    event.createdAt = Instant.now();
    outbox.insert(event);
  }

  private void enqueueWorkflowStart(LearningAssignment assignment) {
    LearningOutbox event = new LearningOutbox();
    event.eventId = "flowable-learning-start:" + assignment.id;
    event.eventType = "FLOWABLE_PROCESS_START";
    event.aggregateId = String.valueOf(assignment.id);
    event.payloadJson = "{}";
    event.status = "PENDING";
    event.nextAttemptAt = Instant.now();
    event.createdAt = Instant.now();
    outbox.insert(event);
  }

  private void markRestored(long assignmentId, String operationId) {
    LearningAssignment current = get(assignmentId);
    if ("RESTORED".equals(current.status)) return;
    current.status = "RESTORED";
    mapper.updateById(current);
    LearningOutbox audit = new LearningOutbox();
    audit.eventId = operationId;
    audit.eventType = "AUTH_PERMISSION_RESTORE";
    audit.aggregateId = String.valueOf(assignmentId);
    audit.payloadJson =
        write(
            Map.of(
                "authUserId", current.authUserId,
                "permissionCode", "judgement:submit",
                "reason", "Flowable qualification workflow completed"));
    audit.status = "PUBLISHED";
    audit.attempts = 1;
    audit.publishedAt = Instant.now();
    audit.createdAt = Instant.now();
    outbox.insert(audit);
  }

  private record ExamOutcome(boolean allCorrect, double score) {}
}

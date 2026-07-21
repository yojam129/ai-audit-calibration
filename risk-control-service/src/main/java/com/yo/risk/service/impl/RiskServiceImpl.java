package com.yo.risk.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.security.context.CurrentUserContext;
import com.yo.risk.domain.dto.*;
import com.yo.risk.domain.po.*;
import com.yo.risk.domain.vo.*;
import com.yo.risk.enums.*;
import com.yo.risk.mapper.*;
import com.yo.risk.service.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskServiceImpl implements RiskService {
  private final RiskProfileMapper mapper;
  private final RiskConsumedEventMapper consumedEvents;
  private final ObjectMapper json;
  private final RiskOutboxMapper outbox;
  private final RiskPolicyMapper policies;
  private final ReviewerErrorFocusMapper errorFocus;
  private final ReviewerQualificationStateMapper qualifications;
  private final int min;
  private final double watch, high;

  public RiskServiceImpl(
      RiskProfileMapper m,
      RiskConsumedEventMapper consumedEvents,
      RiskOutboxMapper outbox,
      RiskPolicyMapper policies,
      ReviewerErrorFocusMapper errorFocus,
      ReviewerQualificationStateMapper qualifications,
      ObjectMapper j,
      @Value("${risk.min-samples:30}") int min,
      @Value("${risk.watch-accuracy:0.95}") double watch,
      @Value("${risk.high-accuracy:0.90}") double high) {
    this.mapper = m;
    this.consumedEvents = consumedEvents;
    this.outbox = outbox;
    this.policies = policies;
    this.errorFocus = errorFocus;
    this.qualifications = qualifications;
    this.json = j;
    this.min = min;
    this.watch = watch;
    this.high = high;
  }

  @Transactional
  public RiskMetricVO record(ReviewOutcomeDTO o) {
    if (consumedEvents.selectCount(
            new LambdaQueryWrapper<RiskConsumedEvent>()
                .eq(RiskConsumedEvent::getEventId, o.eventId()))
        > 0) return get(o.reviewerId());
    var consumedEvent = new RiskConsumedEvent();
    consumedEvent.setEventId(o.eventId());
    consumedEvent.setConsumedAt(Instant.now());
    consumedEvents.insert(consumedEvent);
    if (!o.correct() && o.targetCode() != null) saveErrorFocus(o);
    Instant w =
        o.occurredAt()
            .atZone(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC);
    RiskProfile p = find(o.reviewerId(), w);
    if (p == null) {
      p = new RiskProfile();
      p.reviewerId = o.reviewerId();
      p.windowStart = w;
      p.errorCountsJson = "{}";
    }
    p.reviewed++;
    if (o.correct()) p.correctCount++;
    p.totalDurationMs += o.durationMs();
    Map<String, Long> errors = errors(p.errorCountsJson);
    if (!o.correct() && o.errorType() != null) errors.merge(o.errorType(), 1L, Long::sum);
    double acc = (double) p.correctCount / p.reviewed;
    RiskLevel level =
        p.reviewed < min
            ? RiskLevel.INSUFFICIENT_DATA
            : acc < high ? RiskLevel.HIGH : acc < watch ? RiskLevel.WATCH : RiskLevel.NORMAL;
    p.level = level.name();
    ReviewerQualificationState qualification = qualification(o);
    List<Boolean> recentResults = recentResults(qualification.getRecentResultsJson());
    recentResults.add(o.correct());
    if (recentResults.size() > 50) recentResults.removeFirst();
    int recentCorrect = (int) recentResults.stream().filter(Boolean::booleanValue).count();
    qualification.setRecentReviewed(recentResults.size());
    qualification.setRecentCorrectCount(recentCorrect);
    qualification.setRecentResultsJson(write(recentResults));
    double recentAccuracy = (double) recentCorrect / recentResults.size();
    boolean qualificationFailed = recentResults.size() == 50
        && recentAccuracy < qualificationThreshold();
    boolean triggerTraining = qualificationFailed && !qualification.isTrainingRequired();
    qualification.setTrainingRequired(
        qualification.isTrainingRequired() || qualificationFailed);
    p.trainingRequired = qualification.isTrainingRequired();
    try {
      p.errorCountsJson = json.writeValueAsString(errors);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    if (p.id == null) mapper.insert(p);
    else if (mapper.updateProfile(p) != 1) throw new IllegalStateException("risk profile version conflict");
    saveQualification(qualification);
    if (triggerTraining) {
      var event = new RiskOutbox();
      event.id = UUID.randomUUID();
      event.routingKey = "training.triggered.v1";
      String dominant =
          errors.entrySet().stream()
              .max(Map.Entry.comparingByValue())
              .map(Map.Entry::getKey)
              .orElse("GENERAL");
      try {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", event.id.toString());
        payload.put("reviewerId", o.reviewerId());
        payload.put("authUserId", o.authUserId());
        payload.put("courseCode", "COURSE-FN-001");
        payload.put("errorType", dominant);
        payload.put("dueDays", 7);
        payload.put("riskLevel", "HIGH");
        payload.put("durationMs", o.durationMs());
        payload.put("occurredAt", o.occurredAt());
        putIfPresent(payload, "sampleId", o.sampleId());
        putIfPresent(payload, "sampleNo", o.sampleNo());
        putIfPresent(payload, "chamber", o.chamber());
        putIfPresent(payload, "channelCode", o.channelCode());
        putIfPresent(payload, "targetCode", o.targetCode());
        event.payload = json.writeValueAsString(payload);
      } catch (Exception ex) {
        throw new IllegalStateException(ex);
      }
      event.status = "PENDING";
      event.nextAttemptAt = Instant.now();
      event.createdAt = Instant.now();
      outbox.insert(event);
    }
    return vo(p, errors);
  }

  public RiskMetricVO get(String reviewer) {
    RiskProfile p =
        mapper.selectOne(
            new LambdaQueryWrapper<RiskProfile>()
                .eq(RiskProfile::getReviewerId, reviewer)
                .orderByDesc(RiskProfile::getWindowStart)
                .last("limit 1"));
    if (p == null) throw new NoSuchElementException("risk profile not found");
    return vo(p, errors(p.errorCountsJson));
  }

  public IPage<RiskMetricVO> page(long current, long size, String level) {
    var query = new LambdaQueryWrapper<RiskProfile>()
        .inSql(RiskProfile::getId, "SELECT MAX(id) FROM risk_profile GROUP BY reviewer_id")
        .orderByDesc(RiskProfile::getWindowStart);
    if (level != null && !level.isBlank()) query.eq(RiskProfile::getLevel, level);
    return mapper
        .selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), query)
        .convert(p -> vo(p, errors(p.errorCountsJson)));
  }

  public IPage<ReviewerErrorFocusVO> errors(
      long current, long size, String reviewerId) {
    var query = new LambdaQueryWrapper<ReviewerErrorFocus>()
        .eq(reviewerId != null && !reviewerId.isBlank(), ReviewerErrorFocus::getReviewerId, reviewerId)
        .orderByDesc(ReviewerErrorFocus::getOccurredAt);
    return errorFocus.selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), query)
        .convert(this::errorVO);
  }

  public RiskPolicyVO policy() {
    RiskPolicy policy = currentPolicy();
    RiskPolicyVO result = new RiskPolicyVO();
    result.setQualificationAccuracyThreshold(policy.getQualificationAccuracyThreshold());
    result.setUpdatedBy(policy.getUpdatedBy());
    result.setUpdatedAt(policy.getUpdatedAt());
    return result;
  }

  @Transactional
  public RiskPolicyVO updatePolicy(UpdateRiskPolicyDTO input) {
    RiskPolicy policy = currentPolicy();
    policy.setQualificationAccuracyThreshold(input.getQualificationAccuracyThreshold());
    policy.setUpdatedBy(CurrentUserContext.required().userId());
    policy.setUpdatedAt(Instant.now());
    policies.updateById(policy);
    return policy();
  }

  @Override
  public void recordErrorFocus(ReviewOutcomeDTO outcome) {
    if (!outcome.correct() && outcome.targetCode() != null) saveErrorFocus(outcome);
  }

  @Override
  @Transactional
  public void resetQualificationWindow(String reviewerId) {
    ReviewerQualificationState state = qualifications.selectById(reviewerId);
    if (state == null) return;
    state.setRecentReviewed(0);
    state.setRecentCorrectCount(0);
    state.setRecentResultsJson("[]");
    state.setTrainingRequired(false);
    state.setResetAt(Instant.now());
    if (qualifications.updateState(state) != 1)
      throw new IllegalStateException("qualification state version conflict");
  }

  private RiskProfile find(String r, Instant w) {
    return mapper.selectOne(
        new LambdaQueryWrapper<RiskProfile>()
            .eq(RiskProfile::getReviewerId, r)
            .eq(RiskProfile::getWindowStart, w));
  }

  private Map<String, Long> errors(String s) {
    try {
      return json.readValue(s, new TypeReference<>() {});
    } catch (Exception e) {
      return new HashMap<>();
    }
  }

  private List<Boolean> recentResults(String value) {
    try {
      return new ArrayList<>(json.readValue(value, new TypeReference<List<Boolean>>() {}));
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private ReviewerQualificationState qualification(ReviewOutcomeDTO outcome) {
    ReviewerQualificationState state = qualifications.selectById(outcome.reviewerId());
    if (state != null) {
      if (state.getAuthUserId() == null) state.setAuthUserId(outcome.authUserId());
      return state;
    }
    state = new ReviewerQualificationState();
    state.setReviewerId(outcome.reviewerId());
    state.setAuthUserId(outcome.authUserId());
    state.setRecentResultsJson("[]");
    return state;
  }

  private void saveQualification(ReviewerQualificationState state) {
    if (qualifications.selectById(state.getReviewerId()) == null) qualifications.insert(state);
    else if (qualifications.updateState(state) != 1)
      throw new IllegalStateException("qualification state version conflict");
  }

  private double qualificationThreshold() {
    return currentPolicy().getQualificationAccuracyThreshold();
  }

  private RiskPolicy currentPolicy() {
    RiskPolicy policy = policies.selectById(1L);
    if (policy == null) throw new IllegalStateException("risk policy not initialized");
    return policy;
  }

  private void saveErrorFocus(ReviewOutcomeDTO outcome) {
    ReviewerErrorFocus focus = new ReviewerErrorFocus();
    focus.setEventId(outcome.eventId());
    focus.setReviewerId(outcome.reviewerId());
    focus.setAuthUserId(outcome.authUserId());
    focus.setSampleId(outcome.sampleId());
    focus.setSampleNo(outcome.sampleNo());
    focus.setChamber(outcome.chamber());
    focus.setChannelCode(outcome.channelCode());
    focus.setTargetCode(Optional.ofNullable(outcome.targetCode()).orElse("UNKNOWN"));
    focus.setPredictedLabel(Optional.ofNullable(outcome.predictedLabel()).orElse("UNKNOWN"));
    focus.setTruthLabel(Optional.ofNullable(outcome.truthLabel()).orElse("UNKNOWN"));
    focus.setErrorType(Optional.ofNullable(outcome.errorType()).orElse("MISJUDGEMENT"));
    focus.setOccurredAt(outcome.occurredAt());
    focus.setCreatedAt(Instant.now());
    errorFocus.upsert(focus);
  }

  private ReviewerErrorFocusVO errorVO(ReviewerErrorFocus focus) {
    ReviewerErrorFocusVO result = new ReviewerErrorFocusVO();
    result.setId(focus.getId());
    result.setReviewerId(focus.getReviewerId());
    result.setSampleId(focus.getSampleId());
    result.setSampleNo(focus.getSampleNo());
    result.setChamber(focus.getChamber());
    result.setChannelCode(focus.getChannelCode());
    result.setTargetCode(focus.getTargetCode());
    result.setPredictedLabel(focus.getPredictedLabel());
    result.setTruthLabel(focus.getTruthLabel());
    result.setErrorType(focus.getErrorType());
    result.setOccurredAt(focus.getOccurredAt());
    return result;
  }

  private void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (value != null) target.put(key, value);
  }

  private RiskMetricVO vo(RiskProfile p, Map<String, Long> e) {
    RiskTotalsRow totals = mapper.totals(p.reviewerId);
    ReviewerQualificationState qualification = qualifications.selectById(p.reviewerId);
    long totalReviewed = totals == null ? p.reviewed : totals.getReviewed();
    long totalCorrect = totals == null ? p.correctCount : totals.getCorrect();
    long totalDuration = totals == null ? p.totalDurationMs : totals.getTotalDurationMs();
    int recentReviewed = qualification == null ? 0 : qualification.getRecentReviewed();
    int recentCorrect = qualification == null ? 0 : qualification.getRecentCorrectCount();
    return new RiskMetricVO(
        p.reviewerId,
        p.windowStart,
        totalReviewed,
        totalCorrect,
        totalReviewed == 0 ? 0 : (double) totalCorrect / totalReviewed,
        recentReviewed,
        recentCorrect,
        recentReviewed == 0 ? 0 : (double) recentCorrect / recentReviewed,
        recentReviewed == 50,
        totalReviewed == 0 ? 0 : (double) totalDuration / totalReviewed,
        e,
        RiskLevel.valueOf(p.level),
        qualification != null && qualification.isTrainingRequired());
  }
}

package com.yo.judgement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.sample.SampleClient;
import com.yo.judgement.domain.dto.*;
import com.yo.judgement.domain.po.*;
import com.yo.judgement.domain.vo.*;
import com.yo.judgement.enums.JudgementEnums.*;
import com.yo.judgement.mapper.*;
import com.yo.judgement.mq.*;
import com.yo.judgement.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

@Service
public class ComparisonServiceImpl implements ComparisonService {
  private final ComparisonRunMapper mapper;
  private final OutboxEventMapper outbox;
  private final ObjectMapper json;
  private final SampleClient samples;

  public ComparisonServiceImpl(
      ComparisonRunMapper m, OutboxEventMapper outbox, ObjectMapper json, SampleClient samples) {
    mapper = m;
    this.outbox = outbox;
    this.json = json;
    this.samples = samples;
  }

  @Transactional
  public ComparisonVO compare(ComparisonDTO r) {
    if (r.sampleId() == null || r.targets() == null || r.targets().isEmpty())
      throw new IllegalArgumentException("sampleId/targets required");
    if (mapper.selectCount(
            new LambdaQueryWrapper<ComparisonRunPO>()
                .eq(ComparisonRunPO::getSampleId, r.sampleId())
                .eq(ComparisonRunPO::getComparisonVersion, r.comparisonVersion()))
        > 0) throw new IllegalStateException("comparison already exists");
    var details = r.targets().stream().map(this::target).toList();
    var type =
        details.stream()
            .map(ComparisonVO.TargetVO::consistency)
            .max(Comparator.comparingInt(this::rank))
            .orElse(Consistency.ALL_AGREE);
    int risk = details.stream().mapToInt(ComparisonVO.TargetVO::riskRank).max().orElse(0);
    var reasons = details.stream().flatMap(x -> x.reasonCodes().stream()).distinct().toList();
    var vo = new ComparisonVO(
        r.sampleId(), r.comparisonVersion(), r.primaryReviewerId(), r.primaryAuthUserId(),
        r.primaryDurationMs(), type, risk, reasons, details);
    var po = new ComparisonRunPO();
    po.id = UUID.randomUUID();
    po.sampleId = r.sampleId();
    po.comparisonVersion = r.comparisonVersion();
    po.consistency = type.name();
    po.riskRank = risk;
    po.reasonCodes = String.join(",", reasons);
    try {
      po.targetsJson = json.writeValueAsString(details);
    } catch (Exception ex) {
      throw new IllegalStateException("comparison target serialization failed", ex);
    }
    po.createdAt = Instant.now();
    mapper.insert(po);
    var event =
        new ComparisonPublisher.Event(
            UUID.randomUUID(),
            vo.sampleId(),
            vo.comparisonVersion(),
            vo.primaryReviewerId(),
            vo.primaryAuthUserId(),
            vo.primaryDurationMs(),
            vo.consistency().name(),
            vo.riskRank(),
            vo.reasonCodes(),
            vo.targets(),
            Instant.now());
    var pending = new OutboxEventPO();
    pending.id = event.eventId();
    pending.aggregateType = "COMPARISON";
    pending.aggregateId = po.id;
    pending.eventType = "comparison.completed.v1";
    pending.routingKey = "comparison.completed.v1";
    try {
      pending.payload = json.writeValueAsString(event);
    } catch (Exception ex) {
      throw new IllegalStateException("comparison event serialization failed", ex);
    }
    pending.status = "PENDING";
    pending.nextAttemptAt = Instant.now();
    pending.createdAt = Instant.now();
    outbox.insert(pending);
    return vo;
  }

  @Transactional(readOnly = true)
  public IPage<ComparisonSummaryVO> page(long current, long size, UUID sampleId) {
    return mapper
        .selectSummaryPage(
            new Page<>(Math.max(1, current), Math.max(1, size)),
            sampleId == null ? null : sampleId.toString())
        .convert(this::summary);
  }

  @Transactional(readOnly = true)
  public ComparisonDetailVO detail(UUID id) {
    var row = mapper.selectSummaryById(id.toString());
    if (row == null) throw new NoSuchElementException("comparison not found");
    var detail = new ComparisonDetailVO();
    detail.setId(parseUuid(row.getId()));
    detail.setSampleId(parseUuid(row.getSampleId()));
    detail.setSampleNo(sampleNo(detail.getSampleId()));
    detail.setComparisonVersion(row.getComparisonVersion());
    detail.setConsistency(row.getConsistency());
    detail.setRiskRank(row.getRiskRank());
    detail.setReasonCodes(reasonCodes(row.getReasonCodes()));
    detail.setCreatedAt(row.getCreatedAt());
    detail.setTargets(readTargets(row.getTargetsJson()));
    return detail;
  }

  private ComparisonSummaryVO summary(ComparisonSummaryRow row) {
    var summary = new ComparisonSummaryVO();
    summary.setId(parseUuid(row.getId()));
    summary.setSampleId(parseUuid(row.getSampleId()));
    summary.setSampleNo(sampleNo(summary.getSampleId()));
    summary.setComparisonVersion(row.getComparisonVersion());
    summary.setConsistency(row.getConsistency());
    summary.setRiskRank(row.getRiskRank());
    summary.setReasonCodes(reasonCodes(row.getReasonCodes()));
    summary.setCreatedAt(row.getCreatedAt());
    return summary;
  }

  private UUID parseUuid(String value) {
    try {
      return value == null ? null : UUID.fromString(value);
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  private String sampleNo(UUID sampleId) {
    if (sampleId == null) return "未关联历史样本";
    try {
      var response = samples.getByBusinessId(sampleId);
      var sampleNo = response == null || response.data() == null ? null : response.data().sampleNo();
      return sampleNo == null || sampleNo.isBlank() ? "未关联历史样本" : sampleNo;
    } catch (RuntimeException unavailable) {
      return "未关联历史样本";
    }
  }

  private List<String> reasonCodes(String value) {
    return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
  }

  private List<ComparisonDetailVO.TargetVO> readTargets(String value) {
    if (value == null || value.isBlank()) return List.of();
    try {
      return json.readValue(
          value,
          json.getTypeFactory()
              .constructCollectionType(List.class, ComparisonDetailVO.TargetVO.class));
    } catch (Exception invalidSnapshot) {
      return List.of();
    }
  }

  private ComparisonVO.TargetVO target(ComparisonDTO.TargetDTO t) {
    var s = t.systemLabel();
    var p = t.primaryLabel();
    var a = t.aiLabel();
    List<String> rs = new ArrayList<>();
    Consistency c;
    String d = null;
    int risk;
    if (s == null || p == null || a == null) {
      c = Consistency.UNCERTAIN;
      risk = 3;
      rs.add("UNCERTAIN_OR_INVALID");
    } else if (s != p && s != a && p != a) {
      c = Consistency.ALL_DIFFERENT;
      risk = 4;
      rs.add("ALL_THREE_DIFFERENT");
    } else if (s == Label.INDETERMINATE
        || p == Label.INDETERMINATE
        || a == Label.INDETERMINATE
        || s == Label.INVALID
        || p == Label.INVALID
        || a == Label.INVALID) {
      c = Consistency.UNCERTAIN;
      risk = 3;
      rs.add("UNCERTAIN_OR_INVALID");
    } else if (s == p && p == a) {
      c = Consistency.ALL_AGREE;
      risk = 0;
    } else if (s == p || s == a || p == a) {
      c = Consistency.TWO_AGREE_ONE_DIFF;
      risk = 2;
      d = s == p ? "AI" : s == a ? "PRIMARY" : "SYSTEM";
      rs.add(d + "_DISSENT");
    } else throw new IllegalStateException("unsupported comparison state");
    if (t.aiConfidence() != null && t.aiConfidence() < .6) {
      risk = Math.max(risk, 3);
      rs.add("LOW_AI_CONFIDENCE");
    }
    if (t.internalControl()
        && (s != Label.POSITIVE || p != Label.POSITIVE || a != Label.POSITIVE)) {
      risk = 4;
      rs.add("INTERNAL_CONTROL_FAILED");
    }
    if (t.criticalTarget() && s == Label.NEGATIVE && (p == Label.POSITIVE || a == Label.POSITIVE)) {
      risk = 4;
      rs.add("POSSIBLE_FALSE_NEGATIVE");
    }
    if (t.crossChannelRisk()) {
      risk = Math.max(risk, 3);
      rs.add("CROSS_CHANNEL_INTERFERENCE");
    }
    return new ComparisonVO.TargetVO(t.targetCode(), s, p, a, c, d, risk, List.copyOf(rs));
  }

  private int rank(Consistency x) {
    return switch (x) {
      case ALL_AGREE -> 0;
      case TWO_AGREE_ONE_DIFF -> 1;
      case UNCERTAIN -> 2;
      case ALL_DIFFERENT -> 3;
    };
  }
}

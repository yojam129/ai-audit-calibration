package com.yo.signal.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.api.client.model.ModelRegistryClient;
import com.yo.common.domain.vo.PageVO;
import com.yo.signal.adapter.AiInferenceClient;
import com.yo.signal.domain.dto.StoreCurveDTO;
import com.yo.signal.domain.po.*;
import com.yo.signal.domain.query.SignalQuery;
import com.yo.signal.domain.vo.AiInferenceResultVO;
import com.yo.signal.domain.vo.CurveVO;
import com.yo.signal.domain.vo.FullCurveVO;
import com.yo.signal.mapper.*;
import com.yo.signal.service.SignalService;
import java.nio.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SignalServiceImpl implements SignalService {
  private final CurveRepository curves;
  private final SignalIndexMapper indexes;
  private final AiInferenceResultMapper aiResults;
  private final InferenceOutboxMapper outbox;
  private final AiInferenceClient ai;
  private final ObjectMapper json;
  private final ModelRegistryClient models;
  private final String modelCode;

  public SignalServiceImpl(
      CurveRepository c,
      SignalIndexMapper i,
      AiInferenceResultMapper aiResults,
      InferenceOutboxMapper outbox,
      AiInferenceClient ai,
      ObjectMapper json,
      ModelRegistryClient models,
      @Value("${app.ai-inference.model-code:curve-classifier}") String modelCode) {
    curves = c;
    indexes = i;
    this.aiResults = aiResults;
    this.outbox = outbox;
    this.ai = ai;
    this.json = json;
    this.models = models;
    this.modelCode = modelCode;
  }

  public CurveVO store(StoreCurveDTO d) {
    var old =
        curves.findByRunNoAndChamberAndChannelCodeAndProcessingVersion(
            d.runNo(), d.chamber(), d.channelCode(), d.processingVersion());
    if (old.isPresent()) return vo(old.get());
    var x = new CurveDocument();
    x.runNo = d.runNo();
    x.chamber = d.chamber();
    x.channelCode = d.channelCode();
    x.targetCode = d.targetCode();
    x.processingVersion = d.processingVersion();
    x.rawValues = List.copyOf(d.rawValues());
    x.correctedValues =
        d.correctedValues() == null ? normalize(d.rawValues()) : List.copyOf(d.correctedValues());
    x.ctValue = d.ctValue();
    x.concentrationValue = d.concentrationValue();
    x.concentrationUnit = d.concentrationUnit();
    x.riskLevel = d.riskLevel();
    x.riskFlags = d.riskFlags() == null ? List.of() : List.copyOf(d.riskFlags());
    x.features = features(x.correctedValues);
    x.qcStatus = d.rawValues().size() < 30 ? "INVALID" : "PASS";
    x.checksum = checksum(d.rawValues());
    x.createdAt = Instant.now();
    x = curves.save(x);
    var i = new SignalIndex();
    i.curveId = x.id;
    i.runNo = x.runNo;
    i.chamber = x.chamber;
    i.channelCode = x.channelCode;
    i.targetCode = x.targetCode;
    i.processingVersion = x.processingVersion;
    i.pointCount = x.rawValues.size();
    i.qcStatus = x.qcStatus;
    i.checksum = x.checksum;
    i.createdAt = LocalDateTime.now();
    indexes.insert(i);
    inferAndRecord(x);
    return vo(x);
  }

  private void inferAndRecord(CurveDocument curve) {
    String targetCode =
        curve.targetCode == null || curve.targetCode.isBlank()
            ? curve.channelCode
            : curve.targetCode;
    AiInferenceResult result = aiResults.selectOne(
        Wrappers.<AiInferenceResult>lambdaQuery()
            .eq(AiInferenceResult::getCurveId, curve.id)
            .last("limit 1"));
    boolean insert = result == null;
    if (insert) {
      result = new AiInferenceResult();
      result.curveId = curve.id;
      result.runNo = curve.runNo;
      result.chamber = curve.chamber;
      result.targetCode = targetCode;
      result.createdAt = LocalDateTime.now();
    }
    result.status = "PENDING";
    result.judgement = null;
    result.confidence = null;
    result.evidenceJson = null;
    result.failureReason = null;
    ModelRegistryClient.ModelVersionVO current;
    try {
      current = models.current(modelCode).data();
    } catch (RuntimeException unavailable) {
      current = null;
    }
    if (current == null || !"ACTIVE".equals(current.status())) {
      result.status = "DEGRADED";
      result.failureReason = "NO_ACTIVE_MODEL";
      result.updatedAt = LocalDateTime.now();
      if (insert) aiResults.insert(result); else aiResults.updateById(result);
      return;
    }
    String modelVersion = current.version();
    result.modelVersion = modelVersion;
    result.updatedAt = LocalDateTime.now();
    if (insert) aiResults.insert(result); else aiResults.updateById(result);
    try {
      AiInferenceClient.Result response =
          ai.infer(
              curve.runNo + ":" + curve.chamber + ":" + curve.channelCode,
              targetCode,
              curve.rawValues,
              modelVersion,
              curve.ctValue,
              curve.concentrationValue,
              curve.concentrationUnit,
              curve.riskLevel,
              curve.riskFlags);
      if (response.degraded())
        throw new IllegalStateException(
            "AI_DEGRADED:" + String.join(",", response.degradation_reasons()));
      if (!modelVersion.equals(response.model_version()))
        throw new IllegalStateException(
            "AI_MODEL_VERSION_MISMATCH:expected=" + modelVersion
                + ",actual=" + response.model_version());
      result.status = "COMPLETED";
      result.judgement = response.judgement();
      result.confidence = response.confidence();
      result.modelVersion = response.model_version();
      result.evidenceJson =
          json.writeValueAsString(
              Map.of(
                  "reasonCodes", response.reason_codes(),
                  "inferenceLogic", response.inference_logic(),
                  "features", response.features(),
                  "curveChecksum", curve.checksum));
      result.updatedAt = LocalDateTime.now();
      aiResults.updateById(result);
      aiResults.update(
          null,
          Wrappers.<AiInferenceResult>lambdaUpdate()
              .set(AiInferenceResult::getFailureReason, null)
              .eq(AiInferenceResult::getId, result.id));
      InferenceOutbox event = new InferenceOutbox();
      event.eventId =
          UUID.nameUUIDFromBytes(
                  (curve.id + ":AI_INFERENCE_COMPLETED:" + response.model_version())
                      .getBytes(java.nio.charset.StandardCharsets.UTF_8))
              .toString();
      event.aggregateId = curve.id;
      event.eventType = "AI_INFERENCE_COMPLETED";
      event.payloadJson =
          json.writeValueAsString(
              Map.of(
                  "curveId",
                  curve.id,
                  "runNo",
                  curve.runNo,
                  "judgement",
                  response.judgement(),
                  "modelVersion",
                  response.model_version()));
      event.status = "NEW";
      event.createdAt = LocalDateTime.now();
      event.nextAttemptAt = event.createdAt;
      if (outbox.selectCount(
              Wrappers.<InferenceOutbox>lambdaQuery()
                  .eq(InferenceOutbox::getEventId, event.eventId))
          == 0) outbox.insert(event);
    } catch (Exception failure) {
      result.status = "DEGRADED";
      result.failureReason =
          failure.getMessage() == null
              ? failure.getClass().getSimpleName()
              : failure.getMessage().substring(0, Math.min(500, failure.getMessage().length()));
      result.updatedAt = LocalDateTime.now();
      aiResults.updateById(result);
    }
  }

  @Override
  public int reprocess(String runNo, boolean force) {
    var candidates = aiResults.selectList(
        Wrappers.<AiInferenceResult>lambdaQuery()
            .in(!force, AiInferenceResult::getStatus, "DEGRADED", "PENDING")
            .eq(runNo != null && !runNo.isBlank(), AiInferenceResult::getRunNo, runNo)
            .orderByAsc(AiInferenceResult::getId));
    int processed = 0;
    for (AiInferenceResult candidate : candidates) {
      var curve = curves.findById(candidate.curveId);
      if (curve.isEmpty()) continue;
      inferAndRecord(curve.get());
      processed++;
    }
    return processed;
  }

  @Override
  public synchronized int reprocessPendingBatch(int limit) {
    int batchSize = Math.max(1, Math.min(limit, 100));
    var candidates = aiResults.selectList(
        Wrappers.<AiInferenceResult>lambdaQuery()
            .in(AiInferenceResult::getStatus, "DEGRADED", "PENDING")
            .orderByAsc(AiInferenceResult::getId)
            .last("LIMIT " + batchSize));
    int processed = 0;
    for (AiInferenceResult candidate : candidates) {
      var curve = curves.findById(candidate.curveId);
      if (curve.isEmpty()) continue;
      inferAndRecord(curve.get());
      processed++;
    }
    return processed;
  }

  public List<AiInferenceResultVO> aiResults(String runNo, String targetCode) {
    return aiResults
        .selectList(
            Wrappers.<AiInferenceResult>lambdaQuery()
                .eq(AiInferenceResult::getRunNo, runNo)
                .eq(targetCode != null && !targetCode.isBlank(), AiInferenceResult::getTargetCode, targetCode)
                .orderByAsc(AiInferenceResult::getChamber, AiInferenceResult::getTargetCode))
        .stream()
        .map(
            x ->
                new AiInferenceResultVO(
                    x.id,
                    x.curveId,
                    x.runNo,
                    x.chamber,
                    x.targetCode,
                    x.status,
                    x.judgement,
                    x.confidence,
                    x.evidenceJson,
                    x.modelVersion,
                    x.failureReason,
                    x.updatedAt))
        .toList();
  }

  public PageVO<CurveVO> page(SignalQuery q) {
    var w =
        Wrappers.<SignalIndex>lambdaQuery()
            .like(q.runNo() != null && !q.runNo().isBlank(), SignalIndex::getRunNo, q.runNo())
            .eq(q.chamber() != null && !q.chamber().isBlank(), SignalIndex::getChamber, q.chamber())
            .like(
                q.channelCode() != null && !q.channelCode().isBlank(),
                SignalIndex::getChannelCode,
                q.channelCode())
            .eq(q.qcStatus() != null && !q.qcStatus().isBlank(), SignalIndex::getQcStatus, q.qcStatus())
            .orderByDesc(SignalIndex::getCreatedAt);
    var p = indexes.selectPage(new Page<>(q.pageNo(), q.pageSize()), w);
    var list =
        curves.findAllById(p.getRecords().stream().map(i -> i.curveId).toList()).stream()
            .map(this::vo)
            .toList();
    return new PageVO<>(p.getTotal(), p.getPages(), p.getCurrent(), p.getSize(), list);
  }

  public List<FullCurveVO> curvesByRun(String runNo) {
    return curves.findByRunNoOrderByChamberAscChannelCodeAsc(runNo).stream()
        .map(
            x ->
                new FullCurveVO(
                    x.id,
                    x.runNo,
                    x.chamber,
                    x.channelCode,
                    x.targetCode,
                    x.processingVersion,
                    List.copyOf(x.rawValues),
                    List.copyOf(x.correctedValues),
                    Map.copyOf(x.features),
                    x.qcStatus,
                    x.checksum,
                    x.createdAt))
        .toList();
  }

  static List<Double> normalize(List<Double> v) {
    double b =
        v.stream()
            .limit(Math.min(10, v.size()))
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0);
    return v.stream().map(x -> x - b).toList();
  }

  static Map<String, Double> features(List<Double> v) {
    double min = v.stream().mapToDouble(Double::doubleValue).min().orElse(0),
        max = v.stream().mapToDouble(Double::doubleValue).max().orElse(0);
    return Map.of("min", min, "max", max, "amplitude", max - min);
  }

  static String checksum(List<Double> v) {
    try {
      var m = MessageDigest.getInstance("SHA-256");
      v.forEach(x -> m.update(ByteBuffer.allocate(8).putDouble(x).array()));
      return HexFormat.of().formatHex(m.digest());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private CurveVO vo(CurveDocument x) {
    return new CurveVO(
        x.id,
        x.runNo,
        x.chamber,
        x.channelCode,
        x.processingVersion,
        x.rawValues.size(),
        x.qcStatus,
        x.features,
        x.checksum);
  }
}

package com.yo.sample.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yo.common.domain.vo.PageVO;
import com.yo.sample.domain.dto.CreateSampleDTO;
import com.yo.sample.domain.po.Cartridge;
import com.yo.sample.domain.po.DetectionOrder;
import com.yo.sample.domain.po.InstrumentRun;
import com.yo.sample.domain.po.ReagentLot;
import com.yo.sample.domain.po.Sample;
import com.yo.sample.domain.po.TargetJudgement;
import com.yo.sample.domain.po.SampleOutboxEvent;
import com.yo.sample.domain.query.SampleQuery;
import com.yo.sample.domain.vo.SampleVO;
import com.yo.sample.mapper.CartridgeMapper;
import com.yo.sample.mapper.DetectionOrderMapper;
import com.yo.sample.mapper.InstrumentRunMapper;
import com.yo.sample.mapper.ReagentLotMapper;
import com.yo.sample.mapper.SampleMapper;
import com.yo.sample.mapper.SampleDetailMapper;
import com.yo.sample.mapper.TargetJudgementMapper;
import com.yo.sample.mapper.SampleOutboxMapper;
import com.yo.sample.mapper.PrimaryReviewTaskMapper;
import com.yo.sample.domain.po.PrimaryReviewTask;
import com.yo.sample.domain.vo.SampleDetailVO;
import com.yo.sample.service.SampleService;
import com.yo.sample.mq.DetectionTargetCompletedEvent;
import com.yo.sample.mq.AuditWorkflowEvent;
import com.yo.api.client.signal.SignalClient;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SampleServiceImpl implements SampleService {
  private final SampleMapper mapper;
  private final ReagentLotMapper reagentLots;
  private final CartridgeMapper cartridges;
  private final DetectionOrderMapper orders;
  private final InstrumentRunMapper runs;
  private final TargetJudgementMapper targets;
  private final SampleOutboxMapper outbox;
  private final ObjectMapper json;
  private final PrimaryReviewTaskMapper primaryReviews;
  private final SampleDetailMapper sampleDetails;
  private final SignalClient signals;

  public SampleServiceImpl(
      SampleMapper m,
      ReagentLotMapper reagentLots,
      CartridgeMapper cartridges,
      DetectionOrderMapper orders,
      InstrumentRunMapper runs,
      TargetJudgementMapper targets,
      SampleOutboxMapper outbox,
      PrimaryReviewTaskMapper primaryReviews,
      SampleDetailMapper sampleDetails,
      SignalClient signals,
      ObjectMapper json) {
    mapper = m;
    this.reagentLots = reagentLots;
    this.cartridges = cartridges;
    this.orders = orders;
    this.runs = runs;
    this.targets = targets;
    this.outbox = outbox;
    this.primaryReviews = primaryReviews;
    this.sampleDetails = sampleDetails;
    this.signals = signals;
    this.json = json;
  }

  @Transactional
  public SampleVO create(CreateSampleDTO d) {
    Sample s = new Sample();
    s.businessId = UUID.randomUUID().toString();
    s.sampleNo = "S-" + UUID.randomUUID();
    s.organizationId = d.organizationId();
    s.externalNo = d.externalNo();
    s.specimenType = d.specimenType();
    s.collectedAt = d.collectedAt();
    s.status = "REGISTERED";
    s.createdAt = LocalDateTime.now();
    mapper.insert(s);
    return vo(s);
  }

  public SampleVO get(long id) {
    Sample s = mapper.selectById(id);
    if (s == null) throw new IllegalArgumentException("Sample not found");
    return vo(s);
  }

  @Transactional(readOnly = true)
  public SampleVO getByBusinessId(UUID businessId) {
    Sample sample = mapper.selectOne(
        com.baomidou.mybatisplus.core.toolkit.Wrappers.<Sample>lambdaQuery()
            .eq(Sample::getBusinessId, businessId.toString())
            .last("LIMIT 1"));
    if (sample == null) throw new IllegalArgumentException("Sample not found");
    return vo(sample);
  }

  @Transactional(readOnly = true)
  public String latestRunNo(UUID businessId) {
    String runNo = sampleDetails.selectLatestRunNoByBusinessId(businessId.toString());
    if (runNo == null) throw new IllegalArgumentException("Instrument run not found");
    return runNo;
  }

  public SampleDetailVO detail(long id) {
    SampleVO sample = get(id);
    Map<String, DetectionAccumulator> grouped = new LinkedHashMap<>();
    for (SampleDetailMapper.DetailRow row : sampleDetails.selectSampleDetail(id)) {
      String key = row.orderId() + ":" + row.runId();
      DetectionAccumulator detection = grouped.computeIfAbsent(key, ignored -> new DetectionAccumulator(row));
      if (row.targetCode() != null) {
        detection.targets.add(
            new SampleDetailVO.Target(
                row.chamber(), row.channelCode(), row.targetCode(), row.systemJudgement(),
                row.ctValue(), row.concentrationValue(), row.concentrationUnit(), row.riskLevel(),
                splitFlags(row.riskFlags())));
      }
    }
    return new SampleDetailVO(
        sample, grouped.values().stream().map(DetectionAccumulator::toVO).toList());
  }

  @Transactional
  public com.yo.sample.domain.vo.ImportAggregateVO importAggregate(
      com.yo.sample.domain.dto.ImportAggregateDTO dto) {
    InstrumentRun existing =
        runs.selectOne(
            new QueryWrapper<InstrumentRun>()
                .eq("idempotency_key", dto.idempotencyKey())
                .last("LIMIT 1"));
    if (existing == null) {
      existing = runs.selectOne(
          com.baomidou.mybatisplus.core.toolkit.Wrappers.<InstrumentRun>lambdaQuery()
              .eq(InstrumentRun::getRunNo, dto.runNo())
              .last("LIMIT 1"));
    }
    if (existing != null) {
      DetectionOrder order = orders.selectById(existing.orderId);
      return new com.yo.sample.domain.vo.ImportAggregateVO(
          order.sampleId, order.id, existing.id, existing.runNo, false);
    }

    ReagentLot lot =
        reagentLots.selectOne(
            new QueryWrapper<ReagentLot>()
                .eq("lot_no", Optional.ofNullable(dto.reagentLotNo()).orElse("UNKNOWN"))
                .eq("reagent_code", "PCR")
                .last("LIMIT 1"));
    if (lot == null) {
      lot = new ReagentLot();
      lot.lotNo = Optional.ofNullable(dto.reagentLotNo()).orElse("UNKNOWN");
      lot.reagentCode = "PCR";
      lot.status = "ACTIVE";
      reagentLots.insert(lot);
    }
    Cartridge cartridge =
        cartridges.selectOne(
            new QueryWrapper<Cartridge>()
                .eq("cartridge_no", dto.cartridgeNo())
                .last("LIMIT 1"));
    if (cartridge == null) {
      cartridge = new Cartridge();
      cartridge.cartridgeNo = dto.cartridgeNo();
      cartridge.cartridgeType = "PCR";
      cartridge.reagentLotId = lot.id;
      cartridge.status = "USED";
      cartridges.insert(cartridge);
    }
    Sample sample = new Sample();
    sample.businessId = UUID.randomUUID().toString();
    String readableSampleNo = dto.externalNo() + "-"
        + dto.startedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    sample.sampleNo = readableSampleNo.length() <= 64
        ? readableSampleNo
        : readableSampleNo.substring(readableSampleNo.length() - 64);
    sample.organizationId = dto.organizationId();
    sample.externalNo = dto.externalNo();
    sample.specimenType = "PCR_FLUORESCENCE";
    sample.status = "IMPORTING";
    sample.collectedAt = dto.startedAt();
    sample.createdAt = LocalDateTime.now();
    mapper.insert(sample);
    DetectionOrder order = new DetectionOrder();
    order.orderNo = "O-" + dto.idempotencyKey();
    order.sampleId = sample.id;
    order.assayCode = "MULTIPLEX_PCR";
    order.status = "COMPLETED";
    order.createdAt = LocalDateTime.now();
    orders.insert(order);
    InstrumentRun run = new InstrumentRun();
    run.runNo = dto.runNo();
    run.idempotencyKey = dto.idempotencyKey();
    run.orderId = order.id;
    run.cartridgeId = cartridge.id;
    run.instrumentNo = dto.instrumentNo();
    run.modulePosition = dto.modulePosition();
    run.panelCode = dto.panelCode();
    run.instrumentType = dto.instrumentType();
    run.status = "COMPLETED";
    run.qcStatus = dto.qcStatus();
    run.qcEvidenceJson = dto.qcEvidenceJson();
    run.targetMappingJson = dto.targetMappingJson();
    run.overallResultJson = dto.overallResultJson();
    run.startedAt = dto.startedAt();
    run.endedAt = dto.endedAt();
    runs.insert(run);
    PrimaryReviewTask primaryTask = new PrimaryReviewTask();
    primaryTask.sampleId = sample.id;
    primaryTask.runId = run.id;
    primaryTask.status = "WAITING_IMPORT";
    primaryTask.createdAt = LocalDateTime.now();
    primaryReviews.insert(primaryTask);
    replaceTargets(run.id, dto.targets());
    enqueueDetectionEvent(sample, order, run, lot.lotNo, dto.targets());
    return new com.yo.sample.domain.vo.ImportAggregateVO(
        sample.id, order.id, run.id, run.runNo, true);
  }

  @Override
  @Transactional
  public void markImportCompleted(long sampleId) {
    Sample sample = Optional.ofNullable(mapper.selectById(sampleId)).orElseThrow();
    PrimaryReviewTask primary = primaryReviews.selectOne(
        com.baomidou.mybatisplus.core.toolkit.Wrappers.<PrimaryReviewTask>lambdaQuery()
            .eq(PrimaryReviewTask::getSampleId, sampleId).last("LIMIT 1"));
    if (primary == null) throw new IllegalStateException("primary review task not found");
    InstrumentRun run = runs.selectById(primary.runId);
    if ("IMPORTING".equals(sample.status)) {
      sample.status = "AI_PENDING";
      mapper.updateById(sample);
      primaryReviews.update(
          null,
          com.baomidou.mybatisplus.core.toolkit.Wrappers.<PrimaryReviewTask>lambdaUpdate()
              .set(PrimaryReviewTask::getStatus, "WAITING_AI")
              .eq(PrimaryReviewTask::getId, primary.id)
              .eq(PrimaryReviewTask::getStatus, "WAITING_IMPORT"));
      enqueueWorkflowEvent(sample, run, primary, "IMPORT_COMPLETED");
    }
  }

  @Override
  @Transactional
  public void markAiCompleted(String runNo) {
    InstrumentRun run = runs.selectOne(
        com.baomidou.mybatisplus.core.toolkit.Wrappers.<InstrumentRun>lambdaQuery()
            .eq(InstrumentRun::getRunNo, runNo).last("LIMIT 1"));
    if (run == null) return;
    DetectionOrder order = orders.selectById(run.orderId);
    Sample sample = mapper.selectById(order.sampleId);
    PrimaryReviewTask primary = primaryReviews.selectOne(
        com.baomidou.mybatisplus.core.toolkit.Wrappers.<PrimaryReviewTask>lambdaQuery()
            .eq(PrimaryReviewTask::getRunId, run.id).last("LIMIT 1"));
    if (sample == null || primary == null || !"WAITING_AI".equals(primary.status)) return;
    List<TargetJudgement> expected = targets.selectList(
        com.baomidou.mybatisplus.core.toolkit.Wrappers.<TargetJudgement>query().eq("run_id", run.id));
    var response = signals.aiResults(runNo);
    if (response == null || response.data() == null) return;
    Set<String> completed = new HashSet<>();
    response.data().stream()
        .filter(item -> "COMPLETED".equals(item.status()) && item.judgement() != null)
        .forEach(item -> completed.add(item.chamber() + "|" + item.targetCode()));
    boolean allCompleted = !expected.isEmpty() && expected.stream()
        .allMatch(item -> completed.contains(item.chamber + "|" + item.targetCode));
    if (!allCompleted) return;
    if (primaryReviews.update(
            null,
            com.baomidou.mybatisplus.core.toolkit.Wrappers.<PrimaryReviewTask>lambdaUpdate()
                .set(PrimaryReviewTask::getStatus, "PENDING")
                .eq(PrimaryReviewTask::getId, primary.id)
                .eq(PrimaryReviewTask::getStatus, "WAITING_AI")) != 1) return;
    sample.status = "PRIMARY_PENDING";
    mapper.updateById(sample);
    enqueueWorkflowEvent(sample, run, primary, "AI_COMPLETED");
  }

  private void enqueueWorkflowEvent(
      Sample sample, InstrumentRun run, PrimaryReviewTask primary, String stage) {
    AuditWorkflowEvent event = new AuditWorkflowEvent();
    event.setEventId(UUID.randomUUID());
    event.setSampleId(UUID.fromString(sample.businessId));
    event.setRunNo(run.runNo);
    event.setPrimaryTaskId(primary.id);
    event.setStage(stage);
    event.setOccurredAt(Instant.now());
    SampleOutboxEvent pending = new SampleOutboxEvent();
    pending.id = event.getEventId().toString();
    pending.aggregateType = "SAMPLE_AUDIT";
    pending.aggregateId = sample.businessId;
    pending.eventType = stage;
    pending.routingKey = "sample.audit." + stage.toLowerCase(Locale.ROOT).replace('_', '-') + ".v1";
    try {
      pending.payload = json.writeValueAsString(event);
    } catch (Exception ex) {
      throw new IllegalStateException("workflow event serialization failed", ex);
    }
    pending.status = "PENDING";
    pending.nextAttemptAt = Instant.now();
    pending.createdAt = Instant.now();
    outbox.insert(pending);
  }

  public PageVO<SampleVO> page(SampleQuery q) {
    var w = com.baomidou.mybatisplus.core.toolkit.Wrappers.<Sample>lambdaQuery()
        .like(q.sampleNo() != null && !q.sampleNo().isBlank(), Sample::getSampleNo, q.sampleNo())
        .like(
            q.organizationId() != null && !q.organizationId().isBlank(),
            Sample::getOrganizationId,
            q.organizationId())
        .eq(q.status() != null && !q.status().isBlank(), Sample::getStatus, q.status())
        .orderByDesc(Sample::getCreatedAt);
    var p = mapper.selectPage(new Page<>(q.pageNo(), q.pageSize()), w);
    return new PageVO<>(
        p.getTotal(),
        p.getPages(),
        p.getCurrent(),
        p.getSize(),
        p.getRecords().stream().map(this::vo).toList());
  }

  private SampleVO vo(Sample s) {
    return new SampleVO(
        s.id, s.businessId, s.sampleNo, s.organizationId, s.externalNo, s.specimenType, s.status, s.collectedAt);
  }

  private void replaceTargets(Long runId,
      List<com.yo.sample.domain.dto.ImportAggregateDTO.TargetResultDTO> values) {
    if (values == null) return;
    targets.delete(com.baomidou.mybatisplus.core.toolkit.Wrappers.<TargetJudgement>lambdaQuery()
        .eq(TargetJudgement::getRunId, runId));
    values.forEach(item -> {
      TargetJudgement target = new TargetJudgement();
      target.runId = runId;
      target.chamber = item.chamber();
      target.channelCode = item.channelCode();
      target.targetCode = item.targetCode();
      target.systemJudgement = item.systemJudgement();
      target.ctValue = item.ctValue();
      target.concentrationValue = item.concentrationValue();
      target.concentrationUnit = item.concentrationUnit();
      target.riskLevel = Optional.ofNullable(item.riskLevel()).orElse("NORMAL");
      target.riskFlags = item.riskFlags() == null ? null : String.join(",", item.riskFlags());
      target.createdAt = LocalDateTime.now();
      targets.insert(target);
    });
  }

  private void enqueueDetectionEvent(
      Sample sample, DetectionOrder order, InstrumentRun run, String reagentLotNo,
      List<com.yo.sample.domain.dto.ImportAggregateDTO.TargetResultDTO> values) {
    if (values == null) return;
    DetectionTargetCompletedEvent event = new DetectionTargetCompletedEvent(
        UUID.randomUUID(), sample.organizationId, order.id.toString(), run.instrumentNo,
        run.panelCode, reagentLotNo,
        Optional.ofNullable(run.endedAt).orElse(LocalDateTime.now())
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
        values.stream().map(item -> new DetectionTargetCompletedEvent.TargetResult(
            item.targetCode(), item.systemJudgement(), item.ctValue(), item.concentrationValue(),
            item.concentrationUnit(), item.riskLevel())).toList());
    SampleOutboxEvent pending = new SampleOutboxEvent();
    pending.id = event.eventId().toString();
    pending.aggregateType = "DETECTION_ORDER";
    pending.aggregateId = order.id.toString();
    pending.eventType = "DETECTION_TARGET_COMPLETED";
    pending.routingKey = "detection.target.completed.v1";
    try {
      pending.payload = json.writeValueAsString(event);
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot serialize detection event", failure);
    }
    pending.status = "PENDING";
    pending.nextAttemptAt = Instant.now();
    pending.createdAt = Instant.now();
    outbox.insert(pending);
  }

  private static List<String> splitFlags(String value) {
    return value == null || value.isBlank() ? List.of() : List.of(value.split(","));
  }

  private static final class DetectionAccumulator {
    private final SampleDetailMapper.DetailRow row;
    private final List<SampleDetailVO.Target> targets = new ArrayList<>();

    private DetectionAccumulator(SampleDetailMapper.DetailRow row) {
      this.row = row;
    }

    private SampleDetailVO.Detection toVO() {
      return new SampleDetailVO.Detection(
          row.orderId(), row.orderNo(), row.assayCode(), row.orderStatus(), row.runId(),
          row.runNo(), row.instrumentNo(), row.modulePosition(), row.panelCode(),
          row.instrumentType(), row.runStatus(),
          row.qcStatus(), row.qcEvidenceJson(), row.targetMappingJson(), row.overallResultJson(),
          row.startedAt(), row.endedAt(),
          row.cartridgeNo(), row.reagentLotNo(), List.copyOf(targets));
    }
  }
}

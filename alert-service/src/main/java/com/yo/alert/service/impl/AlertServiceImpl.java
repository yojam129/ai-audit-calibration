package com.yo.alert.service.impl;

import com.yo.alert.domain.po.*;
import com.yo.alert.domain.vo.*;
import com.yo.alert.enums.AlertEnums.*;
import com.yo.alert.mapper.*;
import com.yo.alert.mq.*;
import com.yo.alert.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.yo.alert.infrastructure.FlowableReviewLinkClient;

@Service
public class AlertServiceImpl implements AlertService {
  private final AlertMapper alerts;
  private final ConsumedEventMapper events;
  private final JdbcTemplate jdbc;
  private final FlowableReviewLinkClient flowable;

  public AlertServiceImpl(
      AlertMapper a, ConsumedEventMapper e, JdbcTemplate jdbc,
      FlowableReviewLinkClient flowable) {
    alerts = a;
    events = e;
    this.jdbc = jdbc;
    this.flowable = flowable;
  }

  @Transactional
  public void consume(ComparisonEvent e) {
    if (events.selectById(e.eventId()) != null) return;
    var seen = new ConsumedEventPO();
    seen.eventId = e.eventId();
    seen.consumedAt = Instant.now();
    events.insert(seen);
    if ("ALL_AGREE".equals(e.consistency())) return;
    var p = new AlertPO();
    p.id = UUID.randomUUID();
    p.sampleId = e.sampleId();
    p.comparisonVersion = e.comparisonVersion();
    p.level = level(e.riskRank(), e.reasonCodes()).name();
    p.status = Status.OPEN.name();
    p.reasonCodes = String.join(",", e.reasonCodes());
    p.alertLogic = explain(e.consistency(), e.riskRank(), e.reasonCodes());
    p.slaDueAt = Instant.now().plus(sla(Level.valueOf(p.level)));
    alerts.insert(p);
    synchronize(p);
  }

  @Transactional
  public AlertVO claim(UUID id, String owner, long version) {
    AlertPO alert = alerts.selectById(id);
    if (alert == null)
      throw new IllegalStateException("alert state/version conflict");
    synchronize(alert);
    alert = alerts.selectById(id);
    if (alert.version != version)
      throw new IllegalStateException("alert state/version conflict");
    if (Status.CLAIMED.name().equals(alert.status) && owner.equals(alert.ownerId))
      return vo(alert);
    if (alert.flowableTaskId == null)
      throw new IllegalStateException("secondary review Flowable task is not linked yet");
    flowable.claim(alert.flowableTaskId, owner);
    int updated = jdbc.update(
        """
        UPDATE alert SET status='CLAIMED',owner_id=?,version=version+1
        WHERE id=UUID_TO_BIN(?) AND status IN ('OPEN','ESCALATED') AND version=?
        """,
        owner, id.toString(), version);
    if (updated != 1)
      throw new IllegalStateException("alert state/version conflict");
    return findById(id);
  }

  public List<AlertVO> list() {
    reconcileWorkflowLinks();
    return jdbc.query(
        """
        SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(sample_id) sample_id,comparison_version,
               level,status,reason_codes,alert_logic,sla_due_at,owner_id,
               process_instance_id,flowable_task_id,version
        FROM alert ORDER BY sla_due_at ASC
        """,
        (rs, row) -> readAlert(rs));
  }

  private AlertVO findById(UUID id) {
    return jdbc.queryForObject(
        """
        SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(sample_id) sample_id,comparison_version,
               level,status,reason_codes,alert_logic,sla_due_at,owner_id,
               process_instance_id,flowable_task_id,version
        FROM alert WHERE id=UUID_TO_BIN(?)
        """,
        (rs, row) -> readAlert(rs), id.toString());
  }

  private AlertVO readAlert(ResultSet rs) throws SQLException {
    String reasons = rs.getString("reason_codes");
    return new AlertVO(
        UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("sample_id")),
        rs.getLong("comparison_version"), Level.valueOf(rs.getString("level")),
        Status.valueOf(rs.getString("status")),
        reasons == null || reasons.isBlank() ? List.of() : List.of(reasons.split(",")),
        rs.getString("alert_logic"), rs.getTimestamp("sla_due_at").toInstant(),
        rs.getString("owner_id"), rs.getString("process_instance_id"),
        rs.getString("flowable_task_id"), rs.getLong("version"));
  }

  private Level level(int r, List<String> x) {
    return r >= 4 || x.contains("INTERNAL_CONTROL_FAILED") || x.contains("POSSIBLE_FALSE_NEGATIVE")
        ? Level.P1
        : r >= 2 ? Level.P2 : Level.P3;
  }

  private Duration sla(Level l) {
    return switch (l) {
      case P1 -> Duration.ofMinutes(15);
      case P2 -> Duration.ofHours(2);
      case P3 -> Duration.ofHours(24);
    };
  }

  private AlertVO vo(AlertPO p) {
    return new AlertVO(
        p.id,
        p.sampleId,
        p.comparisonVersion,
        Level.valueOf(p.level),
        Status.valueOf(p.status),
        p.reasonCodes == null ? List.of() : List.of(p.reasonCodes.split(",")),
        p.alertLogic,
        p.slaDueAt,
        p.ownerId,
        p.processInstanceId,
        p.flowableTaskId,
        p.version);
  }

  @Transactional
  public long reconcileWorkflowLinks() {
    long changed = 0;
    for (AlertPO alert : alerts.selectList(
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AlertPO>()
            .ne(AlertPO::getStatus, Status.RESOLVED.name()))) {
      if (synchronize(alert)) changed++;
    }
    return changed;
  }

  private boolean synchronize(AlertPO alert) {
    FlowableReviewLinkClient.ProcessLink link;
    try {
      link = flowable.findSecondaryReview(alert.sampleId);
    } catch (RuntimeException unavailable) {
      return false;
    }
    if (link == null) return false;
    String taskId = link.taskId() == null ? alert.flowableTaskId : link.taskId();
    String owner = link.taskId() == null ? alert.ownerId : link.assignee();
    String status = link.ended() ? Status.RESOLVED.name()
        : link.taskId() == null ? alert.status
        : link.assignee() != null && !link.assignee().isBlank()
            ? Status.CLAIMED.name()
            : Level.P1.name().equals(alert.level)
                ? Status.ESCALATED.name() : Status.OPEN.name();
    boolean changed = !Objects.equals(alert.processInstanceId, link.processInstanceId())
        || !Objects.equals(alert.flowableTaskId, taskId)
        || !Objects.equals(alert.ownerId, owner)
        || !Objects.equals(alert.status, status);
    if (!changed) return false;
    jdbc.update(
        """
        UPDATE alert SET process_instance_id=?,flowable_task_id=?,owner_id=?,status=?
        WHERE id=UUID_TO_BIN(?)
        """,
        link.processInstanceId(), taskId, owner, status, alert.id.toString());
    return true;
  }

  private String explain(String consistency, int riskRank, List<String> reasons) {
    return "Three-way comparison result=" + consistency
        + ", riskRank=" + riskRank
        + ". AI, system and primary-review labels are not fully consistent; "
        + "alert created and routed to secondary review. Evidence=" + String.join(",", reasons) + ".";
  }
}

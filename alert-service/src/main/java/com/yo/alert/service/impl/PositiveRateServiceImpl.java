package com.yo.alert.service.impl;

import com.yo.alert.domain.vo.PositiveRateAlertVO;
import com.yo.alert.domain.vo.PositiveRateRecalculationVO;
import com.yo.alert.mq.DetectionTargetCompletedEvent;
import com.yo.alert.service.PositiveRateService;
import com.yo.alert.service.PositiveRatePolicy;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

@Service
public class PositiveRateServiceImpl implements PositiveRateService {
  private static final Set<String> COUNTABLE =
      Set.of("POSITIVE", "NEGATIVE", "SUSPICIOUS", "INVALID");
  private final JdbcTemplate jdbc;
  private final Duration window;
  private final int minimumDenominator;
  private final BigDecimal deviationThreshold;

  public PositiveRateServiceImpl(
      JdbcTemplate jdbc,
      @Value("${app.positive-rate.window:P7D}") Duration window,
      @Value("${app.positive-rate.minimum-denominator:30}") int minimumDenominator,
      @Value("${app.positive-rate.deviation-threshold:0.15}") BigDecimal deviationThreshold) {
    this.jdbc = jdbc;
    this.window = window;
    this.minimumDenominator = minimumDenominator;
    this.deviationThreshold = deviationThreshold;
  }

  @Override
  @Transactional
  public void consume(DetectionTargetCompletedEvent event) {
    if (event.eventId() == null
        || event.organizationId() == null
        || event.orderId() == null
        || event.occurredAt() == null
        || event.targets() == null) {
      throw new IllegalArgumentException("Detection event lacks denominator dimensions");
    }
    for (var target : event.targets()) {
      if (!COUNTABLE.contains(target.resultLabel()))
        throw new IllegalArgumentException("Unsupported result label: " + target.resultLabel());
      try {
        jdbc.update(
            """
            INSERT INTO detection_target_fact
              (event_id,organization_id,order_id,instrument_no,panel_code,reagent_lot_no,
               target_code,result_label,ct_value,concentration_value,concentration_unit,risk_level,
               occurred_at)
            VALUES (UUID_TO_BIN(?),?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            event.eventId().toString(),
            event.organizationId(),
            event.orderId(),
            event.instrumentNo(),
            event.panelCode(),
            event.reagentLotNo(),
            target.targetCode(),
            target.resultLabel(),
            target.ctValue(),
            target.concentrationValue(),
            target.concentrationUnit(),
            target.riskLevel() == null ? "NORMAL" : target.riskLevel(),
            Timestamp.from(event.occurredAt()));
      } catch (DuplicateKeyException duplicate) {
        // Unique event/target and order/target constraints make at-least-once delivery idempotent.
      }
    }
  }

  @Override
  @Transactional
  public PositiveRateRecalculationVO recalculate(
      String organizationId, String targetCode, Instant windowEnd) {
    Instant start = windowEnd.minus(window);
    Instant baselineStart = start.minus(window);
    Counts current = counts(organizationId, targetCode, start, windowEnd);
    Counts baseline = counts(organizationId, targetCode, baselineStart, start);
    boolean bootstrapped = false;
    if (baseline.denominator < minimumDenominator) {
      Timestamp earliestTimestamp = jdbc.queryForObject(
          """
          SELECT MIN(occurred_at) FROM detection_target_fact
          WHERE organization_id=? AND target_code=? AND occurred_at<?
          """,
          Timestamp.class, organizationId, targetCode, Timestamp.from(windowEnd));
      if (earliestTimestamp != null) {
        Instant earliest = earliestTimestamp.toInstant();
        Duration available = Duration.between(earliest, windowEnd);
        if (available.compareTo(Duration.ofDays(2)) >= 0) {
          Instant midpoint = earliest.plus(available.dividedBy(2));
          Counts bootstrapBaseline = counts(organizationId, targetCode, earliest, midpoint);
          Counts bootstrapCurrent = counts(organizationId, targetCode, midpoint, windowEnd);
          if (bootstrapBaseline.denominator >= minimumDenominator
              && bootstrapCurrent.denominator >= minimumDenominator) {
            start = midpoint;
            baselineStart = earliest;
            baseline = bootstrapBaseline;
            current = bootstrapCurrent;
            bootstrapped = true;
          }
        }
      }
    }
    if (current.denominator < minimumDenominator || baseline.denominator < minimumDenominator) {
      return result(
          organizationId,
          targetCode,
          start,
          windowEnd,
          current,
          baseline,
          false,
          "INSUFFICIENT_DENOMINATOR");
    }
    BigDecimal deviation = current.rate().subtract(baseline.rate()).abs();
    boolean deviates =
        PositiveRatePolicy.deviates(
            current.denominator,
            current.rate(),
            baseline.denominator,
            baseline.rate(),
            minimumDenominator,
            deviationThreshold);
    int inserted = 0;
    if (deviates) {
      inserted =
          jdbc.update(
          """
          INSERT IGNORE INTO positive_rate_alert
            (organization_id,target_code,window_start,window_end,numerator,denominator,
             positive_rate,baseline_numerator,baseline_denominator,baseline_rate,deviation,
             level,status)
          VALUES (?,?,?,?,?,?,?,?,?,?,?,'P2','OPEN')
          """,
          organizationId,
          targetCode,
          Timestamp.from(start),
          Timestamp.from(windowEnd),
          current.numerator,
          current.denominator,
          current.rate(),
          baseline.numerator,
          baseline.denominator,
          baseline.rate(),
          deviation);
    }
    boolean created = inserted == 1;
    return result(
        organizationId,
        targetCode,
        start,
        windowEnd,
        current,
        baseline,
        created,
        deviates
            ? created
                ? bootstrapped ? "BOOTSTRAP_RATE_DEVIATION" : "RATE_DEVIATION"
                : "RATE_DEVIATION_ALREADY_ALERTED"
            : bootstrapped ? "BOOTSTRAP_WITHIN_BASELINE" : "WITHIN_BASELINE");
  }

  @Override
  public List<PositiveRateAlertVO> listAlerts() {
    return jdbc.query(
        """
        SELECT id,organization_id,target_code,window_start,window_end,numerator,denominator,
               positive_rate,baseline_numerator,baseline_denominator,baseline_rate,deviation,
               level,status,created_at
        FROM positive_rate_alert
        ORDER BY created_at DESC
        LIMIT 200
        """,
        (rs, row) -> new PositiveRateAlertVO(
            rs.getLong("id"), rs.getString("organization_id"), rs.getString("target_code"),
            rs.getTimestamp("window_start").toInstant(), rs.getTimestamp("window_end").toInstant(),
            rs.getInt("numerator"), rs.getInt("denominator"), rs.getBigDecimal("positive_rate"),
            rs.getInt("baseline_numerator"), rs.getInt("baseline_denominator"),
            rs.getBigDecimal("baseline_rate"), rs.getBigDecimal("deviation"),
            rs.getString("level"), rs.getString("status"), rs.getTimestamp("created_at").toInstant()));
  }

  @Scheduled(fixedDelayString = "${app.positive-rate.recalculate-delay:30000}")
  public void recalculateLatestWindows() {
    jdbc.queryForList(
        """
        SELECT organization_id,target_code,MAX(occurred_at) AS latest_at
        FROM detection_target_fact
        GROUP BY organization_id,target_code
        """).forEach(row -> recalculate(
            (String) row.get("organization_id"), (String) row.get("target_code"),
            toInstant(row.get("latest_at")).plusSeconds(1)));
  }

  private static Instant toInstant(Object value) {
    if (value instanceof Timestamp timestamp) return timestamp.toInstant();
    if (value instanceof LocalDateTime dateTime)
      return dateTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant();
    throw new IllegalArgumentException("Unsupported database time: " + value);
  }

  private Counts counts(String organizationId, String targetCode, Instant from, Instant to) {
    return jdbc.queryForObject(
        """
        SELECT SUM(result_label='POSITIVE'), COUNT(*)
        FROM detection_target_fact
        WHERE organization_id=? AND target_code=? AND occurred_at>=? AND occurred_at<?
        """,
        (rs, row) -> new Counts(rs.getInt(1), rs.getInt(2)),
        organizationId,
        targetCode,
        Timestamp.from(from),
        Timestamp.from(to));
  }

  private PositiveRateRecalculationVO result(
      String organizationId,
      String targetCode,
      Instant start,
      Instant end,
      Counts current,
      Counts baseline,
      boolean created,
      String reason) {
    return new PositiveRateRecalculationVO(
        organizationId,
        targetCode,
        start,
        end,
        current.numerator,
        current.denominator,
        current.rate(),
        baseline.numerator,
        baseline.denominator,
        baseline.rate(),
        current.rate().subtract(baseline.rate()).abs(),
        created,
        reason);
  }

  record Counts(int numerator, int denominator) {
    BigDecimal rate() {
      return PositiveRatePolicy.rate(numerator, denominator);
    }
  }
}

package com.yo.statistics.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yo.statistics.domain.po.StatisticsPO.*;
import com.yo.statistics.domain.vo.StatisticsVO;
import com.yo.statistics.mapper.StatisticsMappers.*;
import com.yo.statistics.mapper.StatisticsRebuildMapper;
import com.yo.statistics.mq.*;
import com.yo.statistics.service.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StatisticsServiceImpl implements StatisticsService {
  private static final Set<String> ACCURACY_SOURCES = Set.of("SYSTEM", "PRIMARY", "AI");

  private final AccuracyMapper accuracy;
  private final ConfusionMapper confusion;
  private final EventMapper events;
  private final RabbitTemplate rabbit;
  private final DailyAccuracyMapper daily;
  private final StatisticsRedisProjection redis;
  private final OutcomeFactMapper facts;
  private final StatisticsRebuildMapper rebuild;
  private final RedissonClient redisson;
  private final TransactionTemplate transactions;

  public StatisticsServiceImpl(
      AccuracyMapper a,
      ConfusionMapper c,
      EventMapper e,
      DailyAccuracyMapper daily,
      RabbitTemplate rabbit,
      StatisticsRedisProjection redis,
      OutcomeFactMapper facts,
      StatisticsRebuildMapper rebuild,
      RedissonClient redisson,
      PlatformTransactionManager transactionManager) {
    accuracy = a;
    confusion = c;
    events = e;
    this.daily = daily;
    this.rabbit = rabbit;
    this.redis = redis;
    this.facts = facts;
    this.rebuild = rebuild;
    this.redisson = redisson;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  @Transactional
  public void consume(TruthMetricEvent e) {
    if (!e.archived() || e.archivedAt() == null) return;
    if (events.selectCount(
            new LambdaQueryWrapper<Event>().eq(Event::getEventId, e.eventId()))
        > 0) return;
    var seen = new Event();
    seen.eventId = e.eventId();
    seen.consumedAt = Instant.now();
    events.insert(seen);
    Map<String, String> instrumentByTarget = new HashMap<>();
    Map<String, String> aiByTarget = new HashMap<>();
    Map<String, String> humanByTarget = new HashMap<>();
    for (var outcome : e.outcomes()) {
      if ("SYSTEM".equals(outcome.sourceType()))
        instrumentByTarget.put(outcome.targetCode(), outcome.predictedLabel());
      else if ("AI".equals(outcome.sourceType()))
        aiByTarget.put(outcome.targetCode(), outcome.predictedLabel());
      else if ("PRIMARY".equals(outcome.sourceType()))
        humanByTarget.put(outcome.targetCode(), outcome.predictedLabel());
    }
    for (var o : e.outcomes()) {
      var fact = new OutcomeFact();
      fact.eventId = e.eventId();
      fact.sampleId = e.sampleId();
      fact.truthVersion = e.truthVersion();
      fact.targetCode = o.targetCode();
      fact.sourceType = o.sourceType();
      fact.instrumentConclusion = instrumentByTarget.get(o.targetCode());
      fact.aiConclusion = aiByTarget.get(o.targetCode());
      fact.humanConclusion = humanByTarget.get(o.targetCode());
      fact.truthLabel = o.truthLabel();
      fact.reviewerId = o.reviewerId();
      fact.authUserId = o.authUserId();
      fact.durationMs = o.durationMs();
      fact.archived = true;
      fact.secondaryTruthConfirmed = e.secondaryTruthConfirmed();
      fact.archivedAt = e.archivedAt();
      fact.occurredAt = e.occurredAt();
      fact.createdAt = Instant.now();
      facts.insert(fact);
      if (!ACCURACY_SOURCES.contains(o.sourceType())) continue;
      boolean correct = Objects.equals(o.predictedLabel(), o.truthLabel());
      Instant projectionTime = Instant.now();
      accuracy.increment(o.sourceType(), correct ? 1 : 0, projectionTime);
      String key = o.sourceType() + "|" + o.targetCode();
      boolean indeterminate = "INDETERMINATE".equals(o.predictedLabel());
      boolean invalid = "INVALID".equals(o.predictedLabel());
      int tp =
          !indeterminate
                  && !invalid
                  && "POSITIVE".equals(o.truthLabel())
                  && "POSITIVE".equals(o.predictedLabel())
              ? 1
              : 0;
      int fn =
          !indeterminate
                  && !invalid
                  && "POSITIVE".equals(o.truthLabel())
                  && !"POSITIVE".equals(o.predictedLabel())
              ? 1
              : 0;
      int tn =
          !indeterminate
                  && !invalid
                  && "NEGATIVE".equals(o.truthLabel())
                  && "NEGATIVE".equals(o.predictedLabel())
              ? 1
              : 0;
      int fp =
          !indeterminate
                  && !invalid
                  && "NEGATIVE".equals(o.truthLabel())
                  && !"NEGATIVE".equals(o.predictedLabel())
              ? 1
              : 0;
      confusion.increment(
          key,
          o.sourceType(),
          o.targetCode(),
          tp,
          tn,
          fp,
          fn,
          indeterminate ? 1 : 0,
          invalid ? 1 : 0,
          projectionTime);
      LocalDate date = e.occurredAt().atZone(ZoneOffset.UTC).toLocalDate();
      String dailyKey = date + "|" + o.sourceType();
      daily.increment(dailyKey, date, o.sourceType(), correct ? 1 : 0, projectionTime);
      if ("PRIMARY".equals(o.sourceType()) && o.reviewerId() != null) {
        rabbit.convertAndSend(
            "ai.audit.domain",
            "reviewer.outcome.v1",
            Map.of(
                "eventId", e.eventId() + ":" + o.targetCode(),
                "reviewerId", o.reviewerId(),
                "authUserId", o.authUserId(),
                "correct", Objects.equals(o.predictedLabel(), o.truthLabel()),
                "durationMs", o.durationMs(),
                "errorType",
                    Objects.equals(o.predictedLabel(), o.truthLabel())
                        ? "NONE"
                        : o.predictedLabel() + "_AS_" + o.truthLabel(),
                "occurredAt", e.occurredAt()));
      }
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            redis.refresh(mysqlDashboard());
          }
        });
  }

  public StatisticsVO.Dashboard dashboard() {
    return redis
        .get()
        .orElseGet(
            () -> {
              var result = mysqlDashboard();
              redis.refresh(result);
              return result;
            });
  }

  private StatisticsVO.Dashboard mysqlDashboard() {
    var av =
        accuracy.selectList(
                new LambdaQueryWrapper<Accuracy>()
                    .in(Accuracy::getSourceType, ACCURACY_SOURCES))
            .stream()
            .filter(a -> ACCURACY_SOURCES.contains(a.sourceType))
            .map(
                a ->
                    new StatisticsVO.Accuracy(
                        a.sourceType,
                        a.correctCount,
                        a.totalCount,
                        ratio(a.correctCount, a.totalCount)))
            .toList();
    var cv =
        confusion.selectList(
                new LambdaQueryWrapper<Confusion>()
                    .in(Confusion::getSourceType, ACCURACY_SOURCES))
            .stream()
            .filter(c -> ACCURACY_SOURCES.contains(c.sourceType))
            .map(
                c ->
                    new StatisticsVO.Confusion(
                        c.sourceType,
                        c.targetCode,
                        c.tp,
                        c.tn,
                        c.fp,
                        c.fn,
                        c.indeterminate,
                        c.invalidCount,
                        ratio(c.tp, c.tp + c.fn),
                        ratio(c.tn, c.tn + c.fp)))
            .toList();
    return new StatisticsVO.Dashboard(av, cv, events.selectCount(null));
  }

  public List<StatisticsVO.TrendPoint> trend(LocalDate from, LocalDate to) {
    return daily
        .selectList(
            new QueryWrapper<DailyAccuracy>()
                .in("source_type", ACCURACY_SOURCES)
                .ge(from != null, "metric_date", from)
                .le(to != null, "metric_date", to)
                .orderByAsc("metric_date", "source_type"))
        .stream()
        .filter(x -> ACCURACY_SOURCES.contains(x.sourceType))
        .map(
            x ->
                new StatisticsVO.TrendPoint(
                    x.metricDate,
                    x.sourceType,
                    x.correctCount,
                    x.totalCount,
                    ratio(x.correctCount, x.totalCount)))
        .toList();
  }

  public IPage<StatisticsVO.Confusion> inconsistencies(long current, long size, String sourceType) {
    var query =
        new QueryWrapper<Confusion>()
            .apply("(fp + fn + indeterminate + invalid_count) > 0")
            .in("source_type", ACCURACY_SOURCES)
            .eq(sourceType != null && !sourceType.isBlank(), "source_type", sourceType)
            .orderByDesc("(fp + fn + indeterminate + invalid_count)");
    return confusion
        .selectPage(new Page<>(Math.max(1, current), Math.max(1, size)), query)
        .convert(
            c ->
                new StatisticsVO.Confusion(
                    c.sourceType,
                    c.targetCode,
                    c.tp,
                    c.tn,
                    c.fp,
                    c.fn,
                    c.indeterminate,
                    c.invalidCount,
                    ratio(c.tp, c.tp + c.fn),
                ratio(c.tn, c.tn + c.fp)));
  }

  public IPage<StatisticsVO.InconsistencyDetail> inconsistencyDetails(
      long current, long size) {
    return facts.selectInconsistencyDetails(
        new Page<>(Math.max(1, current), Math.max(1, size)));
  }

  private double ratio(long n, long d) {
    return d == 0 ? 0d : (double) n / d;
  }

  public RebuildResult rebuild() {
    var lock = redisson.getLock("ai-audit:statistics:full-rebuild");
    boolean acquired = false;
    try {
      acquired = lock.tryLock(0, 10, TimeUnit.MINUTES);
      if (!acquired) return new RebuildResult(false, facts.selectCount(null));
      return Objects.requireNonNull(transactions.execute(status -> rebuildInTransaction()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Statistics rebuild interrupted", e);
    } finally {
      if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
    }
  }

  private RebuildResult rebuildInTransaction() {
    rebuild.clearAccuracy();
    rebuild.clearConfusion();
    rebuild.clearDaily();
    rebuild.rebuildAccuracy();
    rebuild.rebuildConfusion();
    rebuild.rebuildDaily();
    long count = facts.selectCount(null);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            redis.refresh(mysqlDashboard());
          }
        });
    return new RebuildResult(true, count);
  }
}

package com.yo.scheduler.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xxl.job.core.context.XxlJobContext;
import com.yo.scheduler.domain.JobExecutionPO;
import com.yo.scheduler.mapper.JobExecutionMapper;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class AuditedJobExecutor {
  private final RedissonClient redisson;
  private final JobExecutionMapper audit;
  private final InternalOperationsClient client;
  private final ObjectMapper json;

  public AuditedJobExecutor(
      RedissonClient r, JobExecutionMapper a, InternalOperationsClient c, ObjectMapper j) {
    redisson = r;
    audit = a;
    client = c;
    json = j;
  }

  public void execute(String job, String service, String path, String parameter, Duration timeout) {
    var context = XxlJobContext.getXxlJobContext();
    String invocation =
        context == null
            ? Long.toString(System.currentTimeMillis())
            : context.getJobId() + ":" + context.getJobLogFileName();
    String key = job + ":" + Integer.toHexString(invocation.hashCode());
    if (audit.selectCount(
            new QueryWrapper<JobExecutionPO>().eq("execution_key", key).eq("status", "SUCCESS"))
        > 0) return;
    var lock = redisson.getLock("scheduler:lock:" + job);
    try {
      if (!lock.tryLock(0, timeout.toSeconds() + 30, TimeUnit.SECONDS))
        throw new IllegalStateException("job lock busy: " + job);
      var row =
          audit.selectOne(
              new QueryWrapper<JobExecutionPO>().eq("execution_key", key).last("limit 1"));
      if (row == null) row = new JobExecutionPO();
      row.executionKey = key;
      row.jobName = job;
      row.parameterJson = parameter;
      row.status = "RUNNING";
      row.startedAt = Instant.now();
      if (row.id == null) audit.insert(row);
      else audit.updateById(row);
      try {
        var call =
            CompletableFuture.supplyAsync(() -> client.post(service, path, parameter, timeout));
        try {
          row.resultText = call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutFailure) {
          call.cancel(true);
          throw new IllegalStateException(
              "internal operation timed out after " + timeout, timeoutFailure);
        } catch (ExecutionException operationFailure) {
          throw new IllegalStateException("internal operation failed", operationFailure.getCause());
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("internal operation interrupted", interrupted);
        }
        row.status = "SUCCESS";
      } catch (RuntimeException failure) {
        row.status = "FAILED";
        row.errorText = failure.toString();
        throw failure;
      } finally {
        row.finishedAt = Instant.now();
        row.durationMs = Duration.between(row.startedAt, row.finishedAt).toMillis();
        audit.updateById(row);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("job interrupted", e);
    } finally {
      if (lock.isHeldByCurrentThread()) lock.unlock();
    }
  }
}

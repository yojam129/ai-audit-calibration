package com.yo.scheduler.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;

@TableName("scheduler_job_execution")
public class JobExecutionPO {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String executionKey;
  public String jobName;
  public String parameterJson;
  public String status;
  public Instant startedAt;
  public Instant finishedAt;
  public long durationMs;
  public String resultText;
  public String errorText;
}

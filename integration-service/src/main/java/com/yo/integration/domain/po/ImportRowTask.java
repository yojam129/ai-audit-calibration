package com.yo.integration.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("import_row_task")
@Data
public class ImportRowTask {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long batchId;
  public Integer rowNo;
  public String idempotencyKey;
  public String rowJson;
  public String status;
  public Integer attempts;
  public LocalDateTime nextAttemptAt;
  public String lastError;
  public String processInstanceId;
  public String flowableTaskId;
  public String workflowToken;
  public String recoveryResolution;
  public LocalDateTime recoveryResolvedAt;
  public LocalDateTime createdAt;
  public LocalDateTime updatedAt;
}

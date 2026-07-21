package com.yo.integration.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("import_batch")
@Data
public class ImportBatch {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String batchNo;
  public Long assetId;
  public String businessType;
  public String templateVersion;
  public String status;
  public Integer totalRows;
  public Integer successRows;
  public Integer errorRows;
  public String failureReason;
  public String processInstanceId;
  public String flowableTaskId;
  public String workflowToken;
  public String recoveryResolution;
  public LocalDateTime recoveryResolvedAt;
  @Version public Integer version;
  public LocalDateTime createdAt;
  public LocalDateTime updatedAt;
}

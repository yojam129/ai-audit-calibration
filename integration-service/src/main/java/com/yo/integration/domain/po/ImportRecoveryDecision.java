package com.yo.integration.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("import_recovery_decision")
public class ImportRecoveryDecision {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String workflowToken;
  private String failureScope;
  private Long subjectId;
  private String resolution;
  private String processInstanceId;
  private LocalDateTime resolvedAt;
}

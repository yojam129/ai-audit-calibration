package com.yo.sample.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import lombok.Data;

@TableName("instrument_run")
@Data
public class InstrumentRun {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String runNo;
  public String idempotencyKey;
  public Long orderId;
  public Long cartridgeId;
  public String instrumentNo;
  public String modulePosition;
  public String panelCode;
  public String instrumentType;
  public String status;
  public String qcStatus;
  public String qcEvidenceJson;
  public String targetMappingJson;
  public String overallResultJson;
  public LocalDateTime startedAt;
  public LocalDateTime endedAt;
}

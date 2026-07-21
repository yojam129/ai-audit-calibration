package com.yo.sample.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import lombok.Data;

@TableName("sample")
@Data
public class Sample {
  @TableId(type = IdType.AUTO)
  public Long id;
  public String businessId;

  public String sampleNo;
  public String organizationId;
  public String externalNo;
  public String specimenType;
  public String status;
  public LocalDateTime collectedAt;
  public LocalDateTime createdAt;
}

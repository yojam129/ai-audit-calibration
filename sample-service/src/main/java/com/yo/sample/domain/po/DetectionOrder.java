package com.yo.sample.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;

@TableName("detection_order")
public class DetectionOrder {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String orderNo;
  public Long sampleId;
  public String assayCode;
  public String status;
  public LocalDateTime createdAt;
}

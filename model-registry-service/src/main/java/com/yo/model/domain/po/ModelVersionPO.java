package com.yo.model.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("model_version")
@Data
public class ModelVersionPO {
  @TableId(type = IdType.ASSIGN_ID)
  public Long id;

  public String modelCode;
  public String version;
  public String runtime;
  public String artifactUri;
  public String checksum;
  public String metricsJson;
  public String status;
  public Integer trafficPercent;
  public LocalDateTime createdAt;

}

package com.yo.signal.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@TableName("ai_inference_result")
@Data
public class AiInferenceResult {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String curveId;
  public String runNo;
  public String chamber;
  public String targetCode;
  public String status;
  public String judgement;
  public Double confidence;
  public String evidenceJson;
  public String modelVersion;
  public String failureReason;
  public LocalDateTime createdAt;
  public LocalDateTime updatedAt;

}

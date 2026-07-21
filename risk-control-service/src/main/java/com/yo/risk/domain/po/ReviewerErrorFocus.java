package com.yo.risk.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reviewer_error_focus")
public class ReviewerErrorFocus {
  @TableId(type = IdType.AUTO)
  private Long id;
  private String eventId;
  private String reviewerId;
  private Long authUserId;
  private String sampleId;
  private String sampleNo;
  private String chamber;
  private String channelCode;
  private String targetCode;
  private String predictedLabel;
  private String truthLabel;
  private String errorType;
  private Instant occurredAt;
  private Instant createdAt;
}

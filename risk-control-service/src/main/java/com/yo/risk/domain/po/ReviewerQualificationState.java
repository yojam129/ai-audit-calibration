package com.yo.risk.domain.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.Instant;
import lombok.Data;

@Data
@TableName("reviewer_qualification_state")
public class ReviewerQualificationState {
  @TableId private String reviewerId;
  private Long authUserId;
  private int recentReviewed;
  private int recentCorrectCount;
  private String recentResultsJson;
  private boolean trainingRequired;
  private Instant resetAt;
  @Version private long version;
}

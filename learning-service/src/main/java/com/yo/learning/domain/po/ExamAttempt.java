package com.yo.learning.domain.po;
import com.baomidou.mybatisplus.annotation.*;
import java.time.Instant;
@TableName("exam_attempt")
public class ExamAttempt {
  @TableId(type=IdType.AUTO) public Long id;
  public Long assignmentId;
  public String reviewerId;
  public String status;
  public double score;
  public Instant startedAt;
  public Instant submittedAt;
}

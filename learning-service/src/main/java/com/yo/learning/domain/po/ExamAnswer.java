package com.yo.learning.domain.po;
import com.baomidou.mybatisplus.annotation.*;
@TableName("exam_answer")
public class ExamAnswer {
  @TableId(type=IdType.AUTO) public Long id;
  public Long attemptId;
  public Long questionId;
  public String selectedOptionsJson;
  public boolean correct;
  public int awardedScore;
}

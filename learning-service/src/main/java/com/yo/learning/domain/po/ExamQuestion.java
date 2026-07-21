package com.yo.learning.domain.po;
import com.baomidou.mybatisplus.annotation.*;
@TableName("exam_question")
public class ExamQuestion {
  @TableId(type=IdType.AUTO) public Long id;
  public String courseCode;
  public String stem;
  public String optionsJson;
  public String correctOptionsJson;
  public int score;
  public boolean enabled;
}

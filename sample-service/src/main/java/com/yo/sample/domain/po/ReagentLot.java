package com.yo.sample.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;

@TableName("reagent_lot")
public class ReagentLot {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String lotNo;
  public String reagentCode;
  public String manufacturer;
  public LocalDate manufactureDate;
  public LocalDate expiresDate;
  public String status;
}

package com.yo.sample.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;

@TableName("cartridge")
public class Cartridge {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String cartridgeNo;
  public String cartridgeType;
  public Long reagentLotId;
  public LocalDateTime expiresAt;
  public String status;
}

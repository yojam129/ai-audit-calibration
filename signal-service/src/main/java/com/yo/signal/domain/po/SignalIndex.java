package com.yo.signal.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.*;
import lombok.Data;

@TableName("signal_index")
@Data
public class SignalIndex {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String curveId;
  public String runNo;
  public String chamber;
  public String channelCode;
  public String targetCode;
  public String processingVersion;
  public Integer pointCount;
  public String qcStatus;
  public String checksum;
  public LocalDateTime createdAt;

}

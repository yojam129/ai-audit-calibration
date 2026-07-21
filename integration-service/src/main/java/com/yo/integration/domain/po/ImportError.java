package com.yo.integration.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("import_error")
public class ImportError {
  @TableId(type = IdType.AUTO)
  public Long id;

  public Long batchId;
  public Integer rowNo;
  public String columnName;
  public String errorCode;
  public String errorMessage;
  public LocalDateTime createdAt;
}

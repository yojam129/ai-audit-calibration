package com.yo.integration.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("file_asset")
public class FileAsset {
  @TableId(type = IdType.AUTO)
  public Long id;

  public String assetNo;
  public String bucketName;
  public String objectKey;
  public String originalName;
  public String contentType;
  public Long sizeBytes;
  public String sha256;
  public String status;
  public LocalDateTime createdAt;
}

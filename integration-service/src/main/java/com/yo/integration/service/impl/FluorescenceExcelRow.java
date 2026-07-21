package com.yo.integration.service.impl;

import com.alibaba.excel.annotation.ExcelProperty;

public class FluorescenceExcelRow {
  @ExcelProperty("样本编号")
  public String sampleNo;

  @ExcelProperty("运行编号")
  public String runNo;

  @ExcelProperty("腔室")
  public String chamber;

  @ExcelProperty("通道")
  public String channel;

  @ExcelProperty("原始曲线")
  public String rawCurve;
}

package com.yo.statistics.service;

import com.yo.statistics.domain.vo.StatisticsVO.*;
import com.yo.statistics.mq.*;

public interface StatisticsService {
  void consume(TruthMetricEvent e);

  Dashboard dashboard();

  java.util.List<TrendPoint> trend(java.time.LocalDate from, java.time.LocalDate to);

  com.baomidou.mybatisplus.core.metadata.IPage<Confusion> inconsistencies(
      long current, long size, String sourceType);

  com.baomidou.mybatisplus.core.metadata.IPage<InconsistencyDetail> inconsistencyDetails(
      long current, long size);

  RebuildResult rebuild();

  record RebuildResult(boolean rebuilt, long factCount) {}
}

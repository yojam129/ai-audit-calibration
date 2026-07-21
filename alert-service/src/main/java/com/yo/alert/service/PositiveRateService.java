package com.yo.alert.service;

import com.yo.alert.domain.vo.PositiveRateAlertVO;
import com.yo.alert.domain.vo.PositiveRateRecalculationVO;
import com.yo.alert.mq.DetectionTargetCompletedEvent;
import java.time.Instant;
import java.util.List;

public interface PositiveRateService {
  void consume(DetectionTargetCompletedEvent event);

  PositiveRateRecalculationVO recalculate(
      String organizationId, String targetCode, Instant windowEnd);

  List<PositiveRateAlertVO> listAlerts();
}

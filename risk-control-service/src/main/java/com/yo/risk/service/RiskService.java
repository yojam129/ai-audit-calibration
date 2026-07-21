package com.yo.risk.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.risk.domain.dto.ReviewOutcomeDTO;
import com.yo.risk.domain.dto.UpdateRiskPolicyDTO;
import com.yo.risk.domain.vo.ReviewerErrorFocusVO;
import com.yo.risk.domain.vo.RiskMetricVO;
import com.yo.risk.domain.vo.RiskPolicyVO;

public interface RiskService {
  RiskMetricVO record(ReviewOutcomeDTO outcome);

  RiskMetricVO get(String reviewerId);

  IPage<RiskMetricVO> page(long current, long size, String level);

  IPage<ReviewerErrorFocusVO> errors(long current, long size, String reviewerId);

  RiskPolicyVO policy();

  RiskPolicyVO updatePolicy(UpdateRiskPolicyDTO policy);

  void recordErrorFocus(ReviewOutcomeDTO outcome);

  void resetQualificationWindow(String reviewerId);
}

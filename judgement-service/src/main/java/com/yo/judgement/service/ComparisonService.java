package com.yo.judgement.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.judgement.domain.dto.ComparisonDTO;
import com.yo.judgement.domain.vo.ComparisonDetailVO;
import com.yo.judgement.domain.vo.ComparisonSummaryVO;
import com.yo.judgement.domain.vo.ComparisonVO;
import java.util.UUID;

public interface ComparisonService {
  ComparisonVO compare(ComparisonDTO request);

  IPage<ComparisonSummaryVO> page(long current, long size, UUID sampleId);

  ComparisonDetailVO detail(UUID id);
}

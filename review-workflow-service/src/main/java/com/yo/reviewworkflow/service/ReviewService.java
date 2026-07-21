package com.yo.reviewworkflow.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.reviewworkflow.domain.dto.ReviewDTO;
import com.yo.reviewworkflow.domain.po.GroundTruthPO;
import com.yo.reviewworkflow.domain.vo.ReviewTaskVO;
import com.yo.reviewworkflow.domain.vo.GroundTruthVO;
import java.util.*;

public interface ReviewService {
  void advanceWorkflow(UUID sampleId, String runNo, Long primaryTaskId, String stage);

  ReviewTaskVO create(ReviewDTO.Create request);

  ReviewTaskVO claim(UUID id, ReviewDTO.Claim d);

  GroundTruthPO finalizeTask(UUID id, ReviewDTO.Finalize d);

  IPage<ReviewTaskVO> page(long current, long size, String status);

  ReviewTaskVO detail(UUID id);

  IPage<GroundTruthVO> truthPage(long current, long size, UUID sampleId);

  boolean hasMandatoryReview();
}

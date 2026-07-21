package com.yo.sample.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.yo.sample.domain.dto.PrimaryReviewDTO;
import com.yo.sample.domain.vo.PrimaryReviewTaskVO;

public interface PrimaryReviewService {
  IPage<PrimaryReviewTaskVO> page(long current, long size, String status);
  PrimaryReviewTaskVO detail(long id);
  PrimaryReviewTaskVO claim(long id, PrimaryReviewDTO.Claim request);
  PrimaryReviewTaskVO submit(long id, PrimaryReviewDTO.Submit request);
}

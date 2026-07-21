package com.yo.sample.service;

import com.yo.common.domain.vo.PageVO;
import com.yo.sample.domain.dto.CreateSampleDTO;
import com.yo.sample.domain.dto.ImportAggregateDTO;
import com.yo.sample.domain.query.SampleQuery;
import com.yo.sample.domain.vo.SampleVO;
import com.yo.sample.domain.vo.ImportAggregateVO;
import java.util.UUID;

public interface SampleService {
  SampleVO create(CreateSampleDTO dto);

  ImportAggregateVO importAggregate(ImportAggregateDTO dto);

  void markImportCompleted(long sampleId);

  void markAiCompleted(String runNo);

  PageVO<SampleVO> page(SampleQuery query);

  SampleVO get(long id);

  SampleVO getByBusinessId(UUID businessId);

  String latestRunNo(UUID businessId);

  com.yo.sample.domain.vo.SampleDetailVO detail(long id);
}

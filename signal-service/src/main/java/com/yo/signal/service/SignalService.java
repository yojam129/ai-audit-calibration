package com.yo.signal.service;

import com.yo.common.domain.vo.PageVO;
import com.yo.signal.domain.dto.StoreCurveDTO;
import com.yo.signal.domain.query.SignalQuery;
import com.yo.signal.domain.vo.CurveVO;
import com.yo.signal.domain.vo.FullCurveVO;
import java.util.List;

public interface SignalService {
  CurveVO store(StoreCurveDTO dto);

  PageVO<CurveVO> page(SignalQuery query);

  List<FullCurveVO> curvesByRun(String runNo);

  List<com.yo.signal.domain.vo.AiInferenceResultVO> aiResults(String runNo, String targetCode);

  int reprocess(String runNo, boolean force);

  int reprocessPendingBatch(int limit);
}

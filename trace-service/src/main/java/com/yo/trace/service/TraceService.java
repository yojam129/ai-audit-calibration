package com.yo.trace.service;

import com.yo.trace.domain.dto.*;
import com.yo.trace.domain.query.*;
import com.yo.trace.domain.vo.*;
import java.util.List;

public interface TraceService {
  TraceSearchVO append(AppendTraceDTO x);

  List<TraceSearchVO> search(TraceQuery q);

  ChainVerificationVO verifyChain();

  String archiveManifest();
}

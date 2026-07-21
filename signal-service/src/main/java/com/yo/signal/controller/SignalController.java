package com.yo.signal.controller;

import com.yo.common.domain.vo.*;
import com.yo.signal.domain.dto.StoreCurveDTO;
import com.yo.signal.domain.query.SignalQuery;
import com.yo.signal.domain.vo.AiInferenceResultVO;
import com.yo.signal.domain.vo.CurveVO;
import com.yo.signal.domain.vo.FullCurveVO;
import com.yo.signal.service.SignalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/signals")
public class SignalController {
  private final SignalService service;

  public SignalController(SignalService s) {
    service = s;
  }

  @PostMapping
  public ApiResponse<CurveVO> store(@Valid @RequestBody StoreCurveDTO d) {
    return ApiResponse.ok(service.store(d));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('sample:read')")
  public ApiResponse<PageVO<CurveVO>> page(
      @RequestParam(required = false) String runNo,
      @RequestParam(required = false) String chamber,
      @RequestParam(required = false) String channelCode,
      @RequestParam(required = false) String qcStatus,
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(
        service.page(new SignalQuery(runNo, chamber, channelCode, qcStatus, pageNo, pageSize)));
  }

  @GetMapping("/runs/{runNo}/curves")
  @PreAuthorize("hasAuthority('sample:read') or hasAuthority('judgement:submit') or hasAuthority('review:handle')")
  public ApiResponse<List<FullCurveVO>> curves(@PathVariable String runNo) {
    return ApiResponse.ok(service.curvesByRun(runNo));
  }

  @GetMapping("/ai-results")
  @PreAuthorize("hasAuthority('sample:read') or hasAuthority('risk:manage') or hasAuthority('model:manage') or hasAuthority('internal:call')")
  public ApiResponse<List<AiInferenceResultVO>> aiResults(
      @RequestParam String runNo, @RequestParam(required = false) String targetCode) {
    return ApiResponse.ok(service.aiResults(runNo, targetCode));
  }

  @PostMapping("/inference/reprocess")
  @PreAuthorize("hasAuthority('model:manage') or hasAuthority('risk:manage')")
  public ApiResponse<Integer> reprocess(
      @RequestParam(required = false) String runNo,
      @RequestParam(defaultValue = "false") boolean force) {
    return ApiResponse.ok(service.reprocess(runNo, force));
  }
}

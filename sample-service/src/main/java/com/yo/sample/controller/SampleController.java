package com.yo.sample.controller;

import com.yo.common.domain.vo.*;
import com.yo.sample.domain.dto.CreateSampleDTO;
import com.yo.sample.domain.dto.ImportAggregateDTO;
import com.yo.sample.domain.query.SampleQuery;
import com.yo.sample.domain.vo.SampleVO;
import com.yo.sample.domain.vo.ImportAggregateVO;
import com.yo.sample.service.SampleService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/samples")
public class SampleController {
  private final SampleService service;

  public SampleController(SampleService s) {
    service = s;
  }

  @PostMapping
  public ApiResponse<SampleVO> create(@Valid @RequestBody CreateSampleDTO d) {
    return ApiResponse.ok(service.create(d));
  }

  @PostMapping("/import-aggregate")
  public ApiResponse<ImportAggregateVO> importAggregate(
      @Valid @RequestBody ImportAggregateDTO dto) {
    return ApiResponse.ok(service.importAggregate(dto));
  }

  @PostMapping("/{id}/workflow/import-completed")
  public ApiResponse<Void> importCompleted(@PathVariable long id) {
    service.markImportCompleted(id);
    return ApiResponse.ok(null);
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('sample:read')")
  public ApiResponse<SampleVO> get(@PathVariable long id) {
    return ApiResponse.ok(service.get(id));
  }

  @GetMapping("/business/{businessId}")
  public ApiResponse<SampleVO> getByBusinessId(@PathVariable UUID businessId) {
    return ApiResponse.ok(service.getByBusinessId(businessId));
  }

  @GetMapping("/business/{businessId}/latest-run-no")
  public ApiResponse<String> latestRunNo(@PathVariable UUID businessId) {
    return ApiResponse.ok(service.latestRunNo(businessId));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('sample:read')")
  public ApiResponse<PageVO<SampleVO>> page(
      @RequestParam(required = false) String sampleNo,
      @RequestParam(required = false) String organizationId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(
        service.page(new SampleQuery(sampleNo, organizationId, status, pageNo, pageSize)));
  }

  @GetMapping("/{id}/detail")
  @PreAuthorize("hasAuthority('sample:read')")
  public ApiResponse<com.yo.sample.domain.vo.SampleDetailVO> detail(@PathVariable long id) {
    return ApiResponse.ok(service.detail(id));
  }
}

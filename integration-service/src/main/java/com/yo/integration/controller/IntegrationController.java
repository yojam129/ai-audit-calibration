package com.yo.integration.controller;

import com.yo.common.domain.vo.*;
import com.yo.integration.domain.dto.*;
import com.yo.integration.domain.query.ImportQuery;
import com.yo.integration.domain.vo.*;
import com.yo.integration.service.IntegrationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integration")
public class IntegrationController {
  private final IntegrationService service;

  public IntegrationController(IntegrationService s) {
    service = s;
  }

  @PostMapping("/files/presign")
  public ApiResponse<PresignVO> presign(@Valid @RequestBody PresignDTO d) {
    return ApiResponse.ok(service.presign(d));
  }

  @PostMapping("/files/confirm")
  public ApiResponse<FileAssetVO> confirm(@Valid @RequestBody ConfirmUploadDTO dto) {
    return ApiResponse.ok(service.confirmUpload(dto));
  }

  @GetMapping("/files/{id}")
  public ApiResponse<FileAssetVO> asset(@PathVariable long id) {
    return ApiResponse.ok(service.getAsset(id));
  }

  @PostMapping("/imports")
  public ApiResponse<ImportVO> create(@Valid @RequestBody CreateImportDTO d) {
    return ApiResponse.ok(service.createImport(d));
  }

  @GetMapping("/imports")
  public ApiResponse<PageVO<ImportVO>> page(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String businessType,
      @RequestParam(defaultValue = "1") long pageNo,
      @RequestParam(defaultValue = "20") long pageSize) {
    return ApiResponse.ok(service.page(new ImportQuery(status, businessType, pageNo, pageSize)));
  }

  @GetMapping("/imports/{id}/errors")
  public ApiResponse<List<ImportErrorVO>> errors(@PathVariable long id) {
    return ApiResponse.ok(service.errors(id));
  }

  @PostMapping("/imports/{id}/rows/{rowNo}/retry")
  public ApiResponse<Boolean> retry(@PathVariable long id, @PathVariable int rowNo) {
    return ApiResponse.ok(service.retryFailedRow(id, rowNo));
  }

  @PostMapping("/imports/{id}/rows/{rowNo}/recovery")
  public ApiResponse<Boolean> recover(
      @PathVariable long id,
      @PathVariable int rowNo,
      @Valid @RequestBody ImportRecoveryDTO request) {
    return ApiResponse.ok(
        service.resolveFailedRow(id, rowNo, request.getResolution(), request.getReason()));
  }

  @PostMapping("/imports/{id}/recovery")
  public ApiResponse<ImportVO> recoverBatch(
      @PathVariable long id, @Valid @RequestBody ImportRecoveryDTO request) {
    return ApiResponse.ok(
        service.resolveFailedBatch(id, request.getResolution(), request.getReason()));
  }
}

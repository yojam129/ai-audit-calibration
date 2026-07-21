package com.yo.model.controller;

import com.yo.common.domain.vo.ApiResponse;
import com.yo.model.domain.dto.ModelRegisterDTO;
import com.yo.model.domain.vo.ModelVersionVO;
import com.yo.model.service.ModelRegistryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/models")
public class ModelRegistryController {
  private final ModelRegistryService service;

  public ModelRegistryController(ModelRegistryService service) {
    this.service = service;
  }

  @PostMapping
  public ApiResponse<ModelVersionVO> register(@Valid @RequestBody ModelRegisterDTO dto) {
    return ApiResponse.ok(service.register(dto));
  }

  @PostMapping("/{id}/deployment")
  public ApiResponse<ModelVersionVO> deploy(
      @PathVariable Long id, @RequestParam int trafficPercent) {
    return ApiResponse.ok(service.deploy(id, trafficPercent));
  }

  @GetMapping("/current")
  public ApiResponse<ModelVersionVO> current(@RequestParam String modelCode) {
    return ApiResponse.ok(service.current(modelCode));
  }

  @PostMapping("/{id}/rollback")
  public ApiResponse<ModelVersionVO> rollback(
      @PathVariable Long id, @RequestParam String reason) {
    return ApiResponse.ok(service.rollback(id, reason));
  }
}

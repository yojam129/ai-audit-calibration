package com.yo.auth.controller;
import com.yo.auth.domain.dto.PermissionChangeDTO;
import com.yo.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/internal/auth/permissions")
public class InternalPermissionController {
  private final AuthService service;
  public InternalPermissionController(AuthService service) { this.service = service; }
  @PostMapping("/freeze") public void freeze(@Valid @RequestBody PermissionChangeDTO dto) {
    service.freezePermission(dto);
  }
  @PostMapping("/restore") public void restore(@Valid @RequestBody PermissionChangeDTO dto) {
    service.restorePermission(dto);
  }
}

package com.yo.auth.controller;

import com.yo.auth.domain.dto.LoginDTO;
import com.yo.auth.domain.dto.LogoutDTO;
import com.yo.auth.domain.dto.RefreshTokenDTO;
import com.yo.auth.domain.vo.LoginVO;
import com.yo.auth.service.AuthService;
import com.yo.common.domain.vo.ApiResponse;
import com.yo.security.domain.TokenPairVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {
  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @PostMapping("/login")
  public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
    return ApiResponse.ok(service.login(dto));
  }

  @PostMapping("/refresh")
  public ApiResponse<TokenPairVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
    return ApiResponse.ok(service.refresh(dto.refreshToken()));
  }

  @PostMapping("/logout")
  public ApiResponse<Void> logout(@Valid @RequestBody LogoutDTO dto) {
    service.logout(dto.refreshToken());
    return ApiResponse.ok(null);
  }
}

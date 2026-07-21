package com.yo.auth.service;

import com.yo.auth.domain.dto.LoginDTO;
import com.yo.auth.domain.dto.PermissionChangeDTO;
import com.yo.auth.domain.vo.LoginVO;
import com.yo.security.domain.TokenPairVO;

public interface AuthService {
  LoginVO login(LoginDTO dto);

  TokenPairVO refresh(String refreshToken);

  void logout(String refreshToken);

  void logoutAll(Long userId);

  void freezePermission(PermissionChangeDTO dto);

  void restorePermission(PermissionChangeDTO dto);
}

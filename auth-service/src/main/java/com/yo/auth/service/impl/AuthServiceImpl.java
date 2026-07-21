package com.yo.auth.service.impl;

import com.yo.auth.domain.dto.LoginDTO;
import com.yo.auth.domain.dto.PermissionChangeDTO;
import com.yo.auth.domain.po.UserPO;
import com.yo.auth.domain.vo.LoginVO;
import com.yo.auth.enums.TokenType;
import com.yo.auth.mapper.UserMapper;
import com.yo.auth.service.AuthService;
import com.yo.auth.service.JwtTokenService;
import com.yo.common.domain.vo.CommonResultCode;
import com.yo.common.exception.BizException;
import com.yo.security.domain.AuthenticatedUser;
import com.yo.security.domain.LoginUserVO;
import com.yo.security.domain.TokenPairVO;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
  private static final String REFRESH_PREFIX = "auth:refresh:";
  private static final String VERSION_PREFIX = "auth:version:";
  private final UserMapper mapper;
  private final PasswordEncoder encoder;
  private final JwtTokenService jwt;
  private final StringRedisTemplate redis;

  public AuthServiceImpl(
      UserMapper mapper, PasswordEncoder encoder, JwtTokenService jwt, StringRedisTemplate redis) {
    this.mapper = mapper;
    this.encoder = encoder;
    this.jwt = jwt;
    this.redis = redis;
  }

  @Override
  @Transactional(readOnly = true)
  public LoginVO login(LoginDTO dto) {
    UserPO user =
        mapper
            .findByUsername(dto.username())
            .orElseThrow(() -> new BizException(CommonResultCode.UNAUTHORIZED, "用户名或密码错误"));
    if (!Boolean.TRUE.equals(user.getEnabled())
        || !encoder.matches(dto.password(), user.getPasswordHash()))
      throw new BizException(CommonResultCode.UNAUTHORIZED, "用户名或密码错误");
    AuthenticatedUser principal = principal(user);
    JwtTokenService.IssuedTokens issued = jwt.issue(principal);
    storeRefresh(user.getId(), issued.refreshJti(), issued.refreshTtl());
    LoginUserVO userVO =
        new LoginUserVO(
            user.getId(),
            user.getUsername(),
            user.getDisplayName(),
            user.getOrgId(),
            principal.roles(),
            principal.permissions());
    return new LoginVO(userVO, issued.pair());
  }

  @Override
  @Transactional(readOnly = true)
  public TokenPairVO refresh(String token) {
    Claims claims = jwt.parse(token, TokenType.REFRESH);
    Long userId = Long.valueOf(claims.getSubject());
    String redisKey = REFRESH_PREFIX + claims.getId();
    String owner = redis.opsForValue().get(redisKey);
    if (!userId.toString().equals(owner))
      throw new BizException(CommonResultCode.UNAUTHORIZED, "刷新令牌已失效");
    redis.delete(redisKey);
    UserPO user = mapper.selectById(userId);
    if (user == null
        || !Boolean.TRUE.equals(user.getEnabled())
        || user.getTokenVersion() != ((Number) claims.get("ver")).longValue())
      throw new BizException(CommonResultCode.UNAUTHORIZED, "登录状态已失效");
    JwtTokenService.IssuedTokens issued = jwt.issue(principal(user));
    storeRefresh(userId, issued.refreshJti(), issued.refreshTtl());
    return issued.pair();
  }

  @Override
  public void logout(String token) {
    Claims claims = jwt.parse(token, TokenType.REFRESH);
    redis.delete(REFRESH_PREFIX + claims.getId());
  }

  @Override
  @Transactional
  public void logoutAll(Long userId) {
    UserPO user = mapper.selectById(userId);
    if (user == null) throw new BizException(CommonResultCode.NOT_FOUND);
    user.setTokenVersion(user.getTokenVersion() + 1);
    mapper.updateById(user);
    redis.opsForValue().set(VERSION_PREFIX + userId, user.getTokenVersion().toString());
  }

  @Override
  @Transactional
  public void freezePermission(PermissionChangeDTO dto) {
    changePermission(dto, true, "FREEZE");
  }

  @Override
  @Transactional
  public void restorePermission(PermissionChangeDTO dto) {
    changePermission(dto, false, "RESTORE");
  }

  private void changePermission(PermissionChangeDTO dto, boolean disabled, String operation) {
    long operatorId =
        dto.approvedByAuthUserId() == null ? 0L : dto.approvedByAuthUserId();
    if (mapper.claimOperation(
            dto.operationId(),
            dto.authUserId(),
            operation,
            dto.approvedByAuthUserId(),
            dto.reason())
        == 0) return;
    mapper.setPermissionOverride(
        dto.authUserId(), dto.permissionCode(), disabled, dto.reason(), operatorId);
    mapper.incrementTokenVersion(dto.authUserId());
    UserPO changed = mapper.selectById(dto.authUserId());
    if (changed == null) throw new BizException(CommonResultCode.NOT_FOUND);
    redis
        .opsForValue()
        .set(VERSION_PREFIX + dto.authUserId(), changed.getTokenVersion().toString());
  }

  private AuthenticatedUser principal(UserPO user) {
    Set<String> roles = mapper.findRoles(user.getId());
    Set<String> permissions = mapper.findPermissions(user.getId());
    return new AuthenticatedUser(
        user.getId(),
        user.getOrgId(),
        user.getUsername(),
        roles,
        permissions,
        user.getTokenVersion());
  }

  private void storeRefresh(Long userId, String jti, Duration ttl) {
    redis.opsForValue().set(REFRESH_PREFIX + jti, userId.toString(), ttl);
  }
}

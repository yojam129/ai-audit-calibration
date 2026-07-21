package com.yo.auth.service;

import com.yo.auth.enums.TokenType;
import com.yo.security.domain.AuthenticatedUser;
import com.yo.security.domain.TokenPairVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
  private final SecretKey key;
  private final Duration accessTtl;
  private final Duration refreshTtl;
  private final String issuer;

  public JwtTokenService(
      @Value("${security.jwt.secret}") String secret,
      @Value("${security.jwt.issuer:ai-audit-auth}") String issuer,
      @Value("${security.jwt.access-ttl:PT30M}") Duration accessTtl,
      @Value("${security.jwt.refresh-ttl:P7D}") Duration refreshTtl) {
    if (secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
  }

  public IssuedTokens issue(AuthenticatedUser user) {
    String refreshJti = UUID.randomUUID().toString();
    String access = build(user, TokenType.ACCESS, UUID.randomUUID().toString(), accessTtl);
    String refresh = build(user, TokenType.REFRESH, refreshJti, refreshTtl);
    return new IssuedTokens(
        new TokenPairVO("Bearer", access, accessTtl.toSeconds(), refresh, refreshTtl.toSeconds()),
        refreshJti,
        refreshTtl);
  }

  private String build(AuthenticatedUser u, TokenType type, String jti, Duration ttl) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(issuer)
        .subject(u.userId().toString())
        .id(jti)
        .claim("type", type.name())
        .claim("orgId", u.orgId())
        .claim("username", u.username())
        .claim("roles", u.roles())
        .claim("permissions", u.permissions())
        .claim("ver", u.tokenVersion())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)
        .compact();
  }

  public Claims parse(String token, TokenType required) {
    Claims claims =
        Jwts.parser()
            .verifyWith(key)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    if (!required.name().equals(claims.get("type", String.class)))
      throw new IllegalArgumentException("Wrong token type");
    return claims;
  }

  public record IssuedTokens(TokenPairVO pair, String refreshJti, Duration refreshTtl) {}
}

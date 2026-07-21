package com.yo.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.gateway.config.JwtProperties;
import com.yo.security.web.UserHeaders;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Reactive edge authentication. Deliberately does not use CurrentUserContext/ThreadLocal.
 * Downstream servlet services receive identity only through headers replaced by this filter.
 */
@Component
public class JwtAuthenticationGlobalFilter implements WebFilter, Ordered {
  private final JwtProperties properties;
  private final ReactiveStringRedisTemplate redis;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationGlobalFilter(
      JwtProperties properties, ReactiveStringRedisTemplate redis, ObjectMapper objectMapper) {
    this.properties = properties;
    this.redis = redis;
    this.objectMapper = objectMapper;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, org.springframework.web.server.WebFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (properties.publicPaths().stream().anyMatch(path::equals)
        || exchange.getRequest().getMethod().matches("OPTIONS")) {
      return chain.filter(stripIdentityHeaders(exchange));
    }
    try {
      String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
      if (authorization == null || !authorization.startsWith("Bearer "))
        return unauthorized(exchange, "缺少访问令牌");
      Claims claims =
          Jwts.parser()
              .verifyWith(Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8)))
              .requireIssuer(properties.issuer())
              .build()
              .parseSignedClaims(authorization.substring(7))
              .getPayload();
      if (!"ACCESS".equals(claims.get("type", String.class)))
        return unauthorized(exchange, "令牌类型错误");
      long userId = Long.parseLong(claims.getSubject());
      long tokenVersion = ((Number) claims.get("ver")).longValue();
      return redis
          .opsForValue()
          .get("auth:version:" + userId)
          .flatMap(
              revokedVersion ->
                  Long.parseLong(revokedVersion) > tokenVersion
                      ? unauthorized(exchange, "登录状态已失效")
                      : chain.filter(withIdentity(exchange, claims)))
          .switchIfEmpty(chain.filter(withIdentity(exchange, claims)));
    } catch (RuntimeException invalid) {
      return unauthorized(exchange, "访问令牌无效或已过期");
    }
  }

  private ServerWebExchange stripIdentityHeaders(ServerWebExchange exchange) {
    return exchange
        .mutate()
        .request(
            request ->
                request.headers(
                    headers -> {
                      identityHeaders().forEach(headers::remove);
                    }))
        .build();
  }

  private ServerWebExchange withIdentity(ServerWebExchange exchange, Claims claims) {
    ServerWebExchange sanitized = stripIdentityHeaders(exchange);
    return sanitized
        .mutate()
        .request(
            request ->
                request.headers(
                    headers -> {
                      headers.set(UserHeaders.USER_ID, claims.getSubject());
                      Object orgId = claims.get("orgId");
                      if (orgId != null) headers.set(UserHeaders.ORGANIZATION_ID, orgId.toString());
                      headers.set(
                          UserHeaders.USERNAME,
                          URLEncoder.encode(
                              String.valueOf(claims.get("username")), StandardCharsets.UTF_8));
                      headers.set(UserHeaders.ROLES, join(claims.get("roles")));
                      headers.set(UserHeaders.PERMISSIONS, join(claims.get("permissions")));
                    }))
        .build();
  }

  private static String join(Object value) {
    if (!(value instanceof Collection<?> values)) return "";
    return values.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  private static Set<String> identityHeaders() {
    return Set.of(
        UserHeaders.USER_ID,
        UserHeaders.ORGANIZATION_ID,
        UserHeaders.USERNAME,
        UserHeaders.ROLES,
        UserHeaders.PERMISSIONS,
        UserHeaders.INTERNAL_CALL,
        UserHeaders.INTERNAL_TIMESTAMP,
        UserHeaders.INTERNAL_SIGNATURE);
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    try {
      byte[] body =
          objectMapper.writeValueAsBytes(
              Map.of(
                  "code", 401,
                  "message", message,
                  "data", Map.of(),
                  "traceId", exchange.getRequest().getHeaders().getFirst("X-Trace-Id") == null
                      ? ""
                      : exchange.getRequest().getHeaders().getFirst("X-Trace-Id"),
                  "timestamp", Instant.now().toString()));
      return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    } catch (Exception serializationFailure) {
      return exchange.getResponse().setComplete();
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }
}

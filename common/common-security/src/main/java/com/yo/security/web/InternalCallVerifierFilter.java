package com.yo.security.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public final class InternalCallVerifierFilter extends OncePerRequestFilter {
  public static final String VERIFIED_ATTRIBUTE =
      InternalCallVerifierFilter.class.getName() + ".verified";

  private final InternalCallProperties properties;
  private final InternalNonceStore nonceStore;
  private final Clock clock;
  private final AntPathMatcher paths = new AntPathMatcher();

  public InternalCallVerifierFilter(
      InternalCallProperties properties, InternalNonceStore nonceStore, Clock clock) {
    this.properties = properties;
    this.nonceStore = nonceStore;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (isPublic(request.getRequestURI())) {
      chain.doFilter(request, response);
      return;
    }
    boolean internalPath = matches(properties.internalPaths(), request.getRequestURI());
    boolean declaresInternal = request.getHeader(UserHeaders.INTERNAL_CALL) != null;
    if (!internalPath && !declaresInternal) {
      chain.doFilter(request, response);
      return;
    }
    if (!properties.enabled() || properties.secret() == null || properties.secret().isBlank()) {
      reject(response, "内部调用验签未配置");
      return;
    }
    String timestamp = request.getHeader(UserHeaders.INTERNAL_TIMESTAMP);
    String nonce = request.getHeader(UserHeaders.INTERNAL_NONCE);
    String supplied = request.getHeader(UserHeaders.INTERNAL_SIGNATURE);
    try {
      Instant sentAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
      Duration drift = Duration.between(sentAt, clock.instant()).abs();
      if (drift.compareTo(properties.allowedClockSkew()) > 0
          || nonce == null
          || nonce.isBlank()
          || nonce.length() > 128) {
        reject(response, "内部调用签名已过期或参数无效");
        return;
      }
      String expected =
          InternalCallSignature.sign(
              properties.secret(),
              InternalCallSignature.content(
                  request.getMethod(), request.getRequestURI(), timestamp, nonce));
      if (!InternalCallSignature.constantTimeEquals(expected, supplied)) {
        reject(response, "内部调用签名无效");
        return;
      }
      if (!nonceStore.claim(nonce, properties.allowedClockSkew().multipliedBy(2))) {
        reject(response, "内部调用请求已被使用");
        return;
      }
      request.setAttribute(VERIFIED_ATTRIBUTE, Boolean.TRUE);
      chain.doFilter(request, response);
    } catch (RuntimeException malformed) {
      reject(response, "内部调用签名格式错误");
    }
  }

  private boolean isPublic(String path) {
    return matches(properties.publicPaths(), path);
  }

  private boolean matches(Iterable<String> patterns, String path) {
    for (String pattern : patterns) if (paths.match(pattern, path)) return true;
    return false;
  }

  private static void reject(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"code\":401,\"message\":\""
                + message
                + "\",\"data\":null,\"traceId\":\"\",\"timestamp\":\""
                + Instant.now()
                + "\"}");
  }
}

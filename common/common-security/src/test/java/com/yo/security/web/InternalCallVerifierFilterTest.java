package com.yo.security.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalCallVerifierFilterTest {
  private static final String SECRET = "test-secret-with-at-least-32-characters";
  private static final Instant NOW = Instant.parse("2026-07-19T08:00:00Z");
  private InternalCallVerifierFilter filter;
  private boolean nonceClaimed;

  @BeforeEach
  void setUp() {
    nonceClaimed = false;
    filter =
        new InternalCallVerifierFilter(
            new InternalCallProperties(
                true, SECRET, Duration.ofMinutes(2), List.of("/internal/**"), List.of("/public/**")),
            (nonce, ttl) -> {
              if (nonceClaimed) return false;
              nonceClaimed = true;
              return true;
            },
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void acceptsValidSignatureAndClaimsNonce() throws Exception {
    MockHttpServletRequest request = signedRequest(NOW.getEpochSecond(), "nonce-1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(request.getAttribute(InternalCallVerifierFilter.VERIFIED_ATTRIBUTE)).isEqualTo(true);
    assertThat(nonceClaimed).isTrue();
  }

  @Test
  void rejectsExpiredSignatureBeforeRedisClaim() throws Exception {
    MockHttpServletRequest request = signedRequest(NOW.minusSeconds(121).getEpochSecond(), "nonce-2");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("已过期");
  }

  @Test
  void rejectsReplayedNonce() throws Exception {
    nonceClaimed = true;
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(signedRequest(NOW.getEpochSecond(), "nonce-3"), response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("已被使用");
  }

  @Test
  void rejectsTamperedSignature() throws Exception {
    MockHttpServletRequest request = signedRequest(NOW.getEpochSecond(), "nonce-4");
    request.removeHeader(UserHeaders.INTERNAL_SIGNATURE);
    request.addHeader(UserHeaders.INTERNAL_SIGNATURE, "00");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("签名无效");
  }

  private MockHttpServletRequest signedRequest(long epochSecond, String nonce) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/test");
    String timestamp = Long.toString(epochSecond);
    String signature =
        InternalCallSignature.sign(
            SECRET, InternalCallSignature.content("POST", "/internal/test", timestamp, nonce));
    request.addHeader(UserHeaders.INTERNAL_CALL, "yo-api");
    request.addHeader(UserHeaders.INTERNAL_TIMESTAMP, timestamp);
    request.addHeader(UserHeaders.INTERNAL_NONCE, nonce);
    request.addHeader(UserHeaders.INTERNAL_SIGNATURE, signature);
    return request;
  }
}

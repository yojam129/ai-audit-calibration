package com.yo.api.config;

import com.yo.security.context.CurrentUserContext;
import com.yo.security.web.InternalCallSignature;
import com.yo.security.web.UserHeaders;
import feign.RequestInterceptor;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class FeignInternalConfiguration {
  @Bean
  RequestInterceptor internalHeaders(
      @Value("${security.internal-call.secret:}") String internalSecret) {
    return template -> {
      template.header(UserHeaders.INTERNAL_CALL, "yo-api");
      String traceId = MDC.get("traceId");
      if (traceId != null && !traceId.isBlank()) template.header("X-Trace-Id", traceId);
      CurrentUserContext.current()
          .ifPresent(
              user -> {
                template.header(UserHeaders.USER_ID, user.userId().toString());
                if (user.organizationId() != null)
                  template.header(UserHeaders.ORGANIZATION_ID, user.organizationId().toString());
                template.header(UserHeaders.USERNAME, user.username());
                template.header(UserHeaders.ROLES, String.join(",", user.roles()));
                template.header(UserHeaders.PERMISSIONS, String.join(",", user.permissions()));
              });
      if (!internalSecret.isBlank()) {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString();
        template.header(UserHeaders.INTERNAL_TIMESTAMP, timestamp);
        template.header(UserHeaders.INTERNAL_NONCE, nonce);
        template.header(
            UserHeaders.INTERNAL_SIGNATURE,
            InternalCallSignature.sign(
                internalSecret,
                InternalCallSignature.content(
                    template.method(), template.path(), timestamp, nonce)));
      }
    };
  }
}

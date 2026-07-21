package com.yo.security.web;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.internal-call")
public record InternalCallProperties(
    boolean enabled,
    String secret,
    Duration allowedClockSkew,
    List<String> internalPaths,
    List<String> publicPaths) {

  public InternalCallProperties {
    allowedClockSkew = allowedClockSkew == null ? Duration.ofMinutes(2) : allowedClockSkew;
    internalPaths =
        internalPaths == null || internalPaths.isEmpty() ? List.of("/internal/**") : List.copyOf(internalPaths);
    publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
  }
}

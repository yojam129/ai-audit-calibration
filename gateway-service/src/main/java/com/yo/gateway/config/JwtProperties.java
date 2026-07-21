package com.yo.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String secret, String issuer, List<String> publicPaths) {
  public JwtProperties {
    publicPaths = publicPaths == null ? List.of() : List.copyOf(publicPaths);
  }
}

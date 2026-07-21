package com.yo.security.web;

import com.yo.security.context.CurrentUser;
import com.yo.security.context.CurrentUserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.stream.Stream;

/**
 * Reads identity headers only after the edge gateway has authenticated the request. Services must
 * not be publicly exposed; the gateway always removes client-supplied identity headers.
 */
public final class CurrentUserFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    try {
      String id = request.getHeader(UserHeaders.USER_ID);
      if (id != null && !id.isBlank()) {
        CurrentUser user =
            new CurrentUser(
                Long.valueOf(id),
                nullableLong(request.getHeader(UserHeaders.ORGANIZATION_ID)),
                decode(request.getHeader(UserHeaders.USERNAME)),
                csv(request.getHeader(UserHeaders.ROLES)),
                csv(request.getHeader(UserHeaders.PERMISSIONS)));
        CurrentUserContext.set(user);
        var authorities =
            Stream.concat(
                    user.roles().stream().map(role -> "ROLE_" + role),
                    user.permissions().stream())
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(user, null, authorities));
      } else if (Boolean.TRUE.equals(
          request.getAttribute(InternalCallVerifierFilter.VERIFIED_ATTRIBUTE))) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "internal-service",
                null,
                java.util.List.of(new SimpleGrantedAuthority("internal:call"))));
      }
      chain.doFilter(request, response);
    } finally {
      CurrentUserContext.clear();
      SecurityContextHolder.clearContext();
    }
  }

  private static Long nullableLong(String value) {
    return value == null || value.isBlank() ? null : Long.valueOf(value);
  }

  private static String decode(String value) {
    return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
  }

  private static Set<String> csv(String value) {
    if (value == null || value.isBlank()) return Set.of();
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(item -> !item.isBlank())
        .collect(Collectors.toUnmodifiableSet());
  }
}

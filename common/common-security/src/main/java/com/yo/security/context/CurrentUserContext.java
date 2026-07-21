package com.yo.security.context;

import java.util.Optional;

/** Request-scoped holder for Spring MVC services. Never use this from a WebFlux event loop. */
public final class CurrentUserContext {
  private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

  private CurrentUserContext() {}

  public static void set(CurrentUser user) {
    HOLDER.set(user);
  }

  public static Optional<CurrentUser> current() {
    return Optional.ofNullable(HOLDER.get());
  }

  public static CurrentUser required() {
    return current().orElseThrow(() -> new IllegalStateException("No authenticated user"));
  }

  public static void clear() {
    HOLDER.remove();
  }
}

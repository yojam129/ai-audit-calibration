package com.yo.security.context;

import java.util.concurrent.Callable;

/** Explicitly propagates user context when an MVC request submits work to another thread. */
public record UserContextSnapshot(CurrentUser user) {
  public static UserContextSnapshot capture() {
    return new UserContextSnapshot(CurrentUserContext.current().orElse(null));
  }

  public Runnable wrap(Runnable task) {
    return () -> {
      try {
        if (user != null) CurrentUserContext.set(user);
        task.run();
      } finally {
        CurrentUserContext.clear();
      }
    };
  }

  public <T> Callable<T> wrap(Callable<T> task) {
    return () -> {
      try {
        if (user != null) CurrentUserContext.set(user);
        return task.call();
      } finally {
        CurrentUserContext.clear();
      }
    };
  }
}

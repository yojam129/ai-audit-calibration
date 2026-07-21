package com.yo.security.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UserContextSnapshotTest {
  @AfterEach
  void clean() {
    CurrentUserContext.clear();
  }

  @Test
  void restoresSnapshotForTaskAndAlwaysClearsWorkerThread() {
    CurrentUser user = new CurrentUser(1L, 2L, "admin", Set.of("ADMIN"), Set.of("sample:read"));
    CurrentUserContext.set(user);
    Runnable task =
        () -> {
          assertThat(CurrentUserContext.required()).isEqualTo(user);
        };
    Runnable wrapped = UserContextSnapshot.capture().wrap((Runnable) task);
    CurrentUserContext.clear();

    wrapped.run();

    assertThat(CurrentUserContext.current()).isEmpty();
  }
}

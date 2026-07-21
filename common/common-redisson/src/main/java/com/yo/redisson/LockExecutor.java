package com.yo.redisson;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

public class LockExecutor {
  private final RedissonClient redisson;

  public LockExecutor(RedissonClient redisson) {
    this.redisson = redisson;
  }

  public <T> T execute(String key, Duration wait, Duration lease, Supplier<T> action) {
    RLock lock = redisson.getLock(key);
    boolean acquired = false;
    try {
      acquired = lock.tryLock(wait.toMillis(), lease.toMillis(), TimeUnit.MILLISECONDS);
      if (!acquired) throw new LockAcquisitionException("Could not acquire lock: " + key);
      return action.get();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new LockAcquisitionException("Interrupted while acquiring lock: " + key, interrupted);
    } finally {
      if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
    }
  }

  public void execute(String key, Duration wait, Duration lease, Runnable action) {
    execute(
        key,
        wait,
        lease,
        () -> {
          action.run();
          return null;
        });
  }
}

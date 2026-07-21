package com.yo.redis;

import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;

public class IdempotencyExecutor {
  private final StringRedisTemplate redis;

  public IdempotencyExecutor(StringRedisTemplate redis) {
    this.redis = redis;
  }

  /**
   * Claims a command key. The result means this caller owns execution. A durable domain unique
   * constraint is still required for financial/clinical correctness.
   */
  public boolean claim(String scope, String commandId, Duration ttl) {
    return Boolean.TRUE.equals(
        redis.opsForValue()
            .setIfAbsent(RedisKeys.idempotency(scope, commandId), "PROCESSING", ttl));
  }

  public <T> T executeOnce(
      String scope, String commandId, Duration ttl, Supplier<T> action, Supplier<T> duplicate) {
    String key = RedisKeys.idempotency(scope, commandId);
    if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "PROCESSING", ttl)))
      return duplicate.get();
    try {
      T result = action.get();
      redis.opsForValue().set(key, "COMPLETED", ttl);
      return result;
    } catch (RuntimeException failure) {
      redis.delete(key);
      throw failure;
    }
  }
}

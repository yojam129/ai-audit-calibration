package com.yo.redis;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;

public class CacheHelper {
  private final StringRedisTemplate redis;

  public CacheHelper(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public Optional<String> get(String namespace, String key) {
    return Optional.ofNullable(redis.opsForValue().get(RedisKeys.cache(namespace, key)));
  }

  public String getOrLoad(String namespace, String key, Duration ttl, Supplier<String> loader) {
    return get(namespace, key)
        .orElseGet(
            () -> {
              String value = loader.get();
              if (value != null) redis.opsForValue().set(RedisKeys.cache(namespace, key), value, ttl);
              return value;
            });
  }

  public void evict(String namespace, String key) {
    redis.delete(RedisKeys.cache(namespace, key));
  }
}

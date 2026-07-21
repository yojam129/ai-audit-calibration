package com.yo.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisToolkitAutoConfiguration {
  @Bean
  IdempotencyExecutor idempotencyExecutor(StringRedisTemplate redis) {
    return new IdempotencyExecutor(redis);
  }

  @Bean
  CacheHelper cacheHelper(StringRedisTemplate redis) {
    return new CacheHelper(redis);
  }
}

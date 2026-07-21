package com.yo.redisson;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(RedissonClient.class)
public class RedissonToolkitAutoConfiguration {
  @Bean
  LockExecutor lockExecutor(RedissonClient client) {
    return new LockExecutor(client);
  }
}

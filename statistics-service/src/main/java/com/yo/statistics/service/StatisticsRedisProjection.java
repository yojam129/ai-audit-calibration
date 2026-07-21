package com.yo.statistics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yo.statistics.domain.vo.StatisticsVO.Dashboard;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class StatisticsRedisProjection {
  public static final String DASHBOARD_KEY = "ai-audit:statistics:v1:dashboard";
  private static final Duration TTL = Duration.ofMinutes(10);
  private final ObjectProvider<StringRedisTemplate> redis;
  private final ObjectMapper json;

  public StatisticsRedisProjection(ObjectProvider<StringRedisTemplate> redis, ObjectMapper json) {
    this.redis = redis;
    this.json = json;
  }

  public Optional<Dashboard> get() {
    try {
      var template = redis.getIfAvailable();
      if (template == null) return Optional.empty();
      var value = template.opsForValue().get(DASHBOARD_KEY);
      return value == null ? Optional.empty() : Optional.of(json.readValue(value, Dashboard.class));
    } catch (Exception unavailableOrStale) {
      return Optional.empty();
    }
  }

  public void refresh(Dashboard dashboard) {
    try {
      var template = redis.getIfAvailable();
      if (template != null)
        template.opsForValue().set(DASHBOARD_KEY, json.writeValueAsString(dashboard), TTL);
    } catch (Exception ignored) {
      // MySQL remains authoritative; cache failure must not fail truth consumption.
    }
  }
}

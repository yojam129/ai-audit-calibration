package com.yo.security.web;

import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(InternalCallProperties.class)
@EnableMethodSecurity
public class CurrentUserAutoConfiguration {
  @Bean
  SecurityFilterChain gatewayDelegatedSecurityFilterChain(
      HttpSecurity http, CurrentUserFilter currentUserFilter) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .addFilterBefore(currentUserFilter, AuthorizationFilter.class)
        .build();
  }

  @Bean
  CurrentUserFilter currentUserFilterBean() {
    return new CurrentUserFilter();
  }

  @Bean
  Clock internalCallClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnBean(StringRedisTemplate.class)
  InternalNonceStore internalNonceStore(StringRedisTemplate redis) {
    return (nonce, ttl) ->
        Boolean.TRUE.equals(
            redis.opsForValue().setIfAbsent("yo:audit:internal:nonce:" + nonce, "1", ttl));
  }

  @Bean
  @ConditionalOnMissingBean(InternalNonceStore.class)
  InternalNonceStore inMemoryInternalNonceStore(Clock internalCallClock) {
    var expirations = new ConcurrentHashMap<String, Instant>();
    return (nonce, ttl) -> {
      Instant now = internalCallClock.instant();
      expirations.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
      return expirations.putIfAbsent(nonce, now.plus(ttl)) == null;
    };
  }

  @Bean
  @ConditionalOnBean(InternalNonceStore.class)
  FilterRegistrationBean<InternalCallVerifierFilter> internalCallVerifierFilter(
      InternalCallProperties properties, InternalNonceStore nonceStore, Clock internalCallClock) {
    FilterRegistrationBean<InternalCallVerifierFilter> registration =
        new FilterRegistrationBean<>();
    registration.setFilter(
        new InternalCallVerifierFilter(properties, nonceStore, internalCallClock));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }

  @Bean
  FilterRegistrationBean<CurrentUserFilter> currentUserFilter(CurrentUserFilter filter) {
    FilterRegistrationBean<CurrentUserFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    registration.setEnabled(false);
    return registration;
  }
}

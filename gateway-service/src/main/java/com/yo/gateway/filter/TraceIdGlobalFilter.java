package com.yo.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {
  public static final String HEADER = "X-Trace-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String traceId = exchange.getRequest().getHeaders().getFirst(HEADER);
    if (traceId == null || traceId.isBlank())
      traceId = UUID.randomUUID().toString().replace("-", "");
    ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, traceId).build();
    exchange.getResponse().getHeaders().set(HEADER, traceId);
    return chain.filter(exchange.mutate().request(request).build());
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}

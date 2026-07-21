package com.yo.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class SentinelBlockConfiguration {
  @PostConstruct
  void configureUnifiedBlockResponse() {
    GatewayCallbackManager.setBlockHandler(
        (exchange, failure) ->
            ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .bodyValue(
                    Map.of(
                        "code", 429,
                        "message", "请求过于频繁，请稍后重试",
                        "data", Map.of(),
                        "traceId",
                            exchange.getRequest().getHeaders().getFirst("X-Trace-Id") == null
                                ? ""
                                : exchange.getRequest().getHeaders().getFirst("X-Trace-Id"),
                        "timestamp", Instant.now().toString())));
  }
}

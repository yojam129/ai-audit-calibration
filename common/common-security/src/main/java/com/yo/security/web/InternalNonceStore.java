package com.yo.security.web;

import java.time.Duration;

@FunctionalInterface
public interface InternalNonceStore {
  boolean claim(String nonce, Duration ttl);
}

package com.yo.redis;

public final class RedisKeys {
  private static final String PREFIX = "yo:audit:";

  private RedisKeys() {}

  public static String authVersion(long userId) {
    return PREFIX + "auth:version:" + userId;
  }

  public static String authRefresh(String jti) {
    return PREFIX + "auth:refresh:" + requirePart(jti);
  }

  public static String idempotency(String scope, String key) {
    return PREFIX + "idempotency:" + requirePart(scope) + ":" + requirePart(key);
  }

  public static String cache(String namespace, String key) {
    return PREFIX + "cache:" + requirePart(namespace) + ":" + requirePart(key);
  }

  public static String lock(String namespace, String key) {
    return PREFIX + "lock:" + requirePart(namespace) + ":" + requirePart(key);
  }

  private static String requirePart(String value) {
    if (value == null || value.isBlank() || value.contains(":"))
      throw new IllegalArgumentException("Redis key part must be nonblank and cannot contain ':'");
    return value;
  }
}

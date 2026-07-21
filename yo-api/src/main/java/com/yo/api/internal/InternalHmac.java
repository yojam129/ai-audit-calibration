package com.yo.api.internal;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class InternalHmac {
  private InternalHmac() {}

  public static String sign(
      String method, String path, String timestamp, byte[] body, String secret) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(body);
      String canonical =
          method + "\n" + path + "\n" + timestamp + "\n" + HexFormat.of().formatHex(digest);
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void verify(
      String method, String path, String timestamp, String signature, byte[] body, String secret) {
    if (secret == null || secret.isBlank())
      throw new SecurityException("internal HMAC secret missing");
    long epoch = Long.parseLong(timestamp);
    if (Math.abs(Instant.now().getEpochSecond() - epoch) > 300)
      throw new SecurityException("stale internal request");
    if (!MessageDigest.isEqual(
        sign(method, path, timestamp, body, secret).getBytes(StandardCharsets.US_ASCII),
        signature.getBytes(StandardCharsets.US_ASCII)))
      throw new SecurityException("invalid internal signature");
  }
}

package com.yo.security.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class InternalCallSignature {
  private InternalCallSignature() {}

  public static String content(String method, String path, String timestamp, String nonce) {
    return method + "\n" + path + "\n" + timestamp + "\n" + nonce;
  }

  public static String sign(String secret, String content) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot calculate internal-call signature", failure);
    }
  }

  public static boolean constantTimeEquals(String expectedHex, String suppliedHex) {
    if (expectedHex == null || suppliedHex == null) return false;
    return MessageDigest.isEqual(
        expectedHex.getBytes(StandardCharsets.US_ASCII),
        suppliedHex.getBytes(StandardCharsets.US_ASCII));
  }
}

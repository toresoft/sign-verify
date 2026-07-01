/**
 * sign-verify Copyright (C) 2026 toresoft
 *
 * <p>This file is part of the "sign-verify" project.
 *
 * <p>This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * <p>This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * <p>You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301 USA
 */
package org.toresoft.signverify.adapter.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class HmacSigner {

  // Monotonic unix-seconds counter so back-to-back calls (same wall-clock second)
  // still emit distinct timestamps. Initialized lazily to the call-time epoch.
  private static final AtomicLong TS_COUNTER = new AtomicLong();

  public record SignedHeaders(
      String signature, String timestamp, String nonce, String deliveryId) {}

  public SignedHeaders sign(String algorithm, String secret, byte[] body, String deliveryId) {
    try {
      long ts =
          TS_COUNTER.updateAndGet(
              prev -> {
                long now = System.currentTimeMillis() / 1000;
                return Math.max(now, prev + 1);
              });
      String nonce = UUID.randomUUID().toString();
      String bodyHash = toHex(MessageDigest.getInstance("SHA-256").digest(body));
      String canonical = ts + "\n" + nonce + "\n" + deliveryId + "\n" + bodyHash;
      Mac mac = Mac.getInstance(algorithm);
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
      String sig = toHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
      String prefix = "HmacSHA512".equalsIgnoreCase(algorithm) ? "sha512" : "sha256";
      return new SignedHeaders(prefix + "=" + sig, String.valueOf(ts), nonce, deliveryId);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String toHex(byte[] b) {
    StringBuilder sb = new StringBuilder(b.length * 2);
    for (byte x : b) sb.append(String.format("%02x", x));
    return sb.toString();
  }
}

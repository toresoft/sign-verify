package org.toresoft.signverify.adapter.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.SecretCipherPort;

@Component
public class AesGcmSecretCipherAdapter implements SecretCipherPort {

  private static final int IV_LEN = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom rnd = new SecureRandom();

  public AesGcmSecretCipherAdapter(@Value("${app.security.master-key}") String masterKey) {
    if (masterKey == null || masterKey.isBlank()) {
      throw new IllegalStateException("app.security.master-key not configured");
    }
    byte[] raw = Base64.getDecoder().decode(masterKey);
    if (raw.length != 32) {
      throw new IllegalStateException("master-key must be 256-bit base64");
    }
    this.key = new SecretKeySpec(raw, "AES");
  }

  @Override
  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LEN];
      rnd.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[IV_LEN + ct.length];
      System.arraycopy(iv, 0, out, 0, IV_LEN);
      System.arraycopy(ct, 0, out, IV_LEN, ct.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (Exception e) {
      throw new IllegalStateException("encryption failed", e);
    }
  }

  @Override
  public String decrypt(String cipherB64) {
    try {
      byte[] raw = Base64.getDecoder().decode(cipherB64);
      byte[] iv = new byte[IV_LEN];
      System.arraycopy(raw, 0, iv, 0, IV_LEN);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
      byte[] pt = c.doFinal(raw, IV_LEN, raw.length - IV_LEN);
      return new String(pt, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("decryption failed", e);
    }
  }
}

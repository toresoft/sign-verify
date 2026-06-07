package org.toresoft.signverify.domain.port;

public interface SecretCipherPort {
  String encrypt(String plaintext);

  String decrypt(String cipher);
}

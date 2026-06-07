package org.toresoft.signverify.domain.port;

public interface PasswordHasherPort {
  String hash(String plaintext);

  boolean matches(String plaintext, String hash);
}

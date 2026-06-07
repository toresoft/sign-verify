package org.toresoft.signverify.adapter.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.PasswordHasherPort;

@Component
public class BcryptPasswordHasherAdapter implements PasswordHasherPort {

  private final int cost;

  public BcryptPasswordHasherAdapter(@Value("${app.security.bcrypt-cost:12}") int cost) {
    this.cost = cost;
  }

  @Override
  public String hash(String plaintext) {
    return BCrypt.hashpw(plaintext, BCrypt.gensalt(cost));
  }

  @Override
  public boolean matches(String plaintext, String hash) {
    try {
      return BCrypt.checkpw(plaintext, hash);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}

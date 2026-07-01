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

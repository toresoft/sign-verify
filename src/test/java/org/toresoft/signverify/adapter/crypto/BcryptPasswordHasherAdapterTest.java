package org.toresoft.signverify.adapter.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BcryptPasswordHasherAdapterTest {

  private final BcryptPasswordHasherAdapter hasher = new BcryptPasswordHasherAdapter(12);

  @Test
  void hash_and_verify_roundtrip() {
    String plain = "supersecret";
    String hash = hasher.hash(plain);
    assertThat(hash).isNotEqualTo(plain).startsWith("$2");
    assertThat(hasher.matches(plain, hash)).isTrue();
    assertThat(hasher.matches("other", hash)).isFalse();
  }
}

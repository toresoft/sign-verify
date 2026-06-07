package org.toresoft.signverify.adapter.callback;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class HmacSignerTest {

  private static final String SECRET = "test-secret-key";
  private static final String DELIVERY_ID = "delivery-abc-123";

  private final HmacSigner signer = new HmacSigner();

  @Test
  void sign_returnsAllFourHeaders() {
    byte[] body = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);

    HmacSigner.SignedHeaders headers = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);

    assertThat(headers.signature()).isNotNull().isNotBlank();
    assertThat(headers.timestamp()).isNotNull().isNotBlank();
    assertThat(headers.nonce()).isNotNull().isNotBlank();
    assertThat(headers.deliveryId()).isNotNull().isNotBlank();
  }

  @Test
  void sign_signatureFormatSha256_usesSha256Prefix() {
    byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

    HmacSigner.SignedHeaders headers = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);

    assertThat(headers.signature()).startsWith("sha256=");
  }

  @Test
  void sign_signatureFormatSha512_usesSha512Prefix() {
    byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

    HmacSigner.SignedHeaders headers = signer.sign("HmacSHA512", SECRET, body, DELIVERY_ID);

    assertThat(headers.signature()).startsWith("sha512=");
  }

  @Test
  void sign_consecutiveCalls_produceDifferentNonces() {
    byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

    HmacSigner.SignedHeaders first = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);
    HmacSigner.SignedHeaders second = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);

    // Nonce is random per call — must differ between invocations.
    assertThat(first.nonce()).isNotEqualTo(second.nonce());
    // Timestamp is wall-clock based; both must be unix-seconds longs.
    assertThat(first.timestamp()).isNotEqualTo(second.timestamp());
  }

  @Test
  void sign_hexSignatureLength_isCorrect() {
    byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

    HmacSigner.SignedHeaders sha256Headers = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);
    HmacSigner.SignedHeaders sha512Headers = signer.sign("HmacSHA512", SECRET, body, DELIVERY_ID);

    // 32 raw bytes (SHA-256) -> 64 lowercase hex chars after the prefix.
    String sha256Hex = sha256Headers.signature().substring("sha256=".length());
    assertThat(sha256Hex).hasSize(64).matches("[0-9a-f]+");

    // 64 raw bytes (SHA-512) -> 128 lowercase hex chars after the prefix.
    String sha512Hex = sha512Headers.signature().substring("sha512=".length());
    assertThat(sha512Hex).hasSize(128).matches("[0-9a-f]+");
  }

  @Test
  void sign_nonce_isUuidFormat() {
    byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

    HmacSigner.SignedHeaders headers = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);

    Pattern uuidPattern =
        Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    assertThat(headers.nonce()).matches(uuidPattern);
    // Sanity: must also parse as a real UUID.
    assertThat(UUID.fromString(headers.nonce())).isNotNull();
  }

  @Test
  void sign_timestamp_isUnixSeconds() {
    byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
    // 2024-01-01T00:00:00Z = 1704067200. Anything sane must be well past that.
    long janFirst2024 = 1_704_067_200L;

    HmacSigner.SignedHeaders headers = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);

    long ts = Long.parseLong(headers.timestamp());
    assertThat(ts).isGreaterThan(janFirst2024);
    // Wall-clock sanity: must be within a few minutes of "now".
    long nowSeconds = System.currentTimeMillis() / 1000;
    assertThat(ts).isCloseTo(nowSeconds, org.assertj.core.data.Offset.offset(300L));
  }

  @Test
  void sign_deliveryId_isPreserved() {
    byte[] body = "payload".getBytes(StandardCharsets.UTF_8);

    HmacSigner.SignedHeaders headers = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);

    assertThat(headers.deliveryId()).isEqualTo(DELIVERY_ID);
  }

  @Test
  void sign_emptyBody_stillProducesValidSignature() {
    byte[] body = new byte[0];

    HmacSigner.SignedHeaders headers = signer.sign("HmacSHA256", SECRET, body, DELIVERY_ID);

    // Prefix and hex-length guarantees must still hold for an empty payload.
    assertThat(headers.signature()).startsWith("sha256=");
    String hex = headers.signature().substring("sha256=".length());
    assertThat(hex).hasSize(64).matches("[0-9a-f]+");
    assertThat(headers.deliveryId()).isEqualTo(DELIVERY_ID);
  }
}

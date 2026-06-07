package org.toresoft.signverify.adapter.callback;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.port.CallbackDispatcherPort;

@Component
public class HmacCallbackDispatcherAdapter implements CallbackDispatcherPort {

  private final HmacSigner signer = new HmacSigner();
  private final HttpClient client;
  private final int timeoutMs;
  private final boolean allowHttp;
  private final boolean blockPrivate;

  public HmacCallbackDispatcherAdapter(
      @Value("${app.callback.timeout}") Duration timeout,
      @Value("${app.callback.allow-http}") boolean allowHttp,
      @Value("${app.callback.block-private-networks}") boolean blockPrivate) {
    this.timeoutMs = (int) timeout.toMillis();
    this.allowHttp = allowHttp;
    this.blockPrivate = blockPrivate;
    this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
  }

  @Override
  public DispatchResult dispatch(
      String url,
      String alg,
      String secret,
      byte[] body,
      String jobId,
      String deliveryId,
      int attempt) {
    try {
      URI uri = URI.create(url);
      if (!allowHttp && !"https".equalsIgnoreCase(uri.getScheme())) {
        return new DispatchResult(0, "http_disallowed");
      }
      if (blockPrivate && isPrivate(uri.getHost())) {
        return new DispatchResult(0, "private_network_blocked");
      }
      var sig = signer.sign(alg, secret, body, deliveryId);
      HttpRequest req =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofMillis(timeoutMs))
              .header("Content-Type", "application/json")
              .header("X-Timestamp", sig.timestamp())
              .header("X-Nonce", sig.nonce())
              .header("X-Signature", sig.signature())
              .header("X-Signature-Algorithm", alg)
              .header("X-Job-Id", jobId)
              .header("X-Delivery-Id", deliveryId)
              .header("X-Delivery-Attempt", String.valueOf(attempt))
              .header("User-Agent", "sign-verify/1.0")
              .POST(HttpRequest.BodyPublishers.ofByteArray(body))
              .build();
      HttpResponse<Void> res = client.send(req, HttpResponse.BodyHandlers.discarding());
      return new DispatchResult(res.statusCode(), null);
    } catch (Exception e) {
      return new DispatchResult(0, e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  private boolean isPrivate(String host) {
    if (host == null) return true;
    return host.startsWith("10.")
        || host.startsWith("192.168.")
        || host.startsWith("172.16.")
        || host.startsWith("172.17.")
        || host.startsWith("172.18.")
        || host.startsWith("172.19.")
        || host.startsWith("172.20.")
        || host.startsWith("172.21.")
        || host.startsWith("172.22.")
        || host.startsWith("172.23.")
        || host.startsWith("172.24.")
        || host.startsWith("172.25.")
        || host.startsWith("172.26.")
        || host.startsWith("172.27.")
        || host.startsWith("172.28.")
        || host.startsWith("172.29.")
        || host.startsWith("172.30.")
        || host.startsWith("172.31.")
        || host.equals("localhost")
        || host.equals("127.0.0.1");
  }
}

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

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
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

  /**
   * SSRF guard. Resolves the host to its actual IP address(es) and rejects any that fall in a
   * non-routable / internal range. Resolving (instead of string-matching the hostname) blocks cloud
   * metadata endpoints (169.254.169.254, link-local), decimal/octal/hex IP encodings, IPv6
   * loopback, and DNS names that point at private space. A host that cannot be resolved is treated
   * as private (fail closed).
   */
  private boolean isPrivate(String host) {
    if (host == null || host.isBlank()) return true;
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      if (addresses.length == 0) return true;
      for (InetAddress addr : addresses) {
        if (addr.isLoopbackAddress()
            || addr.isAnyLocalAddress()
            || addr.isLinkLocalAddress()
            || addr.isSiteLocalAddress()
            || addr.isMulticastAddress()
            || isUniqueLocalIpv6(addr)) {
          return true;
        }
      }
      return false;
    } catch (UnknownHostException e) {
      return true;
    }
  }

  /** IPv6 Unique Local Addresses (fc00::/7) are private but not flagged by isSiteLocalAddress(). */
  private boolean isUniqueLocalIpv6(InetAddress addr) {
    byte[] a = addr.getAddress();
    return a.length == 16 && (a[0] & 0xfe) == 0xfc;
  }
}

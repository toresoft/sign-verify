package org.toresoft.signverify.adapter.callback;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.port.CallbackDispatcherPort.DispatchResult;

/**
 * Unit tests for {@link HmacCallbackDispatcherAdapter}. Spins up a local {@link HttpServer} on
 * 127.0.0.1 (a random free port) to exercise the full HTTP path without mocks. The plan
 * deliberately keeps HTTP test-doubles out of the test dependencies, so we use the JDK's built-in
 * {@code com.sun.net.httpserver.HttpServer}.
 */
class HmacCallbackDispatcherAdapterTest {

  private static final byte[] BODY = "{\"hello\":\"world\"}".getBytes(StandardCharsets.UTF_8);
  private static final String SECRET = "test-secret";
  private static final String JOB_ID = "job-42";
  private static final String DELIVERY_ID = "delivery-abc";

  private HttpServer server;
  private String baseUrl;
  private HmacCallbackDispatcherAdapter adapter;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newSingleThreadExecutor());
    int port = server.getAddress().getPort();
    baseUrl = "http://127.0.0.1:" + port;
    server.start();
    // Default: allow HTTP, do not block private — individual tests may override.
    adapter = new HmacCallbackDispatcherAdapter(Duration.ofSeconds(5), true, false);
  }

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  // ---------- Test 1: full request shape ----------

  @Test
  void dispatch_sendsPostWithHmacHeaders() throws Exception {
    CapturingHandler handler = new CapturingHandler(200, "");
    server.createContext("/cb", handler);
    String url = baseUrl + "/cb";

    DispatchResult result =
        adapter.dispatch(url, "HmacSHA256", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    // HTTP response is captured, no error.
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(result.errorMessage()).isNull();
    // The handler observed exactly one request.
    assertThat(handler.latch.await(2, TimeUnit.SECONDS)).isTrue();

    CapturedRequest req = handler.captured.get();
    assertThat(req.method).isEqualTo("POST");
    assertThat(req.headers.get("Content-Type").get(0)).isEqualTo("application/json");
    // HMAC headers must all be present and non-blank.
    assertThat(req.headers).containsKey("X-Signature");
    assertThat(req.headers.get("X-Signature").get(0)).isNotBlank();
    assertThat(req.headers).containsKey("X-Timestamp");
    assertThat(req.headers.get("X-Timestamp").get(0)).isNotBlank();
    assertThat(req.headers).containsKey("X-Nonce");
    assertThat(req.headers.get("X-Nonce").get(0)).isNotBlank();
    assertThat(req.headers).containsKey("X-Signature-Algorithm");
    assertThat(req.headers.get("X-Signature-Algorithm").get(0)).isEqualTo("HmacSHA256");
    assertThat(req.headers).containsKey("X-Job-Id");
    assertThat(req.headers.get("X-Job-Id").get(0)).isEqualTo(JOB_ID);
    assertThat(req.headers).containsKey("X-Delivery-Id");
    assertThat(req.headers.get("X-Delivery-Id").get(0)).isEqualTo(DELIVERY_ID);
    assertThat(req.headers).containsKey("X-Delivery-Attempt");
    assertThat(req.headers.get("X-Delivery-Attempt").get(0)).isEqualTo("1");
    // Body forwarded byte-for-byte.
    assertThat(req.body).isEqualTo(BODY);
  }

  // ---------- Test 2: success status code ----------

  @Test
  void dispatch_returnsSuccessStatusCode() {
    server.createContext("/ok", new FixedStatusHandler(200, ""));
    String url = baseUrl + "/ok";

    DispatchResult result =
        adapter.dispatch(url, "HmacSHA256", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(result.errorMessage()).isNull();
  }

  // ---------- Test 3: server error ----------

  @Test
  void dispatch_returnsServerErrorStatus() {
    server.createContext("/boom", new FixedStatusHandler(500, "boom"));
    String url = baseUrl + "/boom";

    DispatchResult result =
        adapter.dispatch(url, "HmacSHA256", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    assertThat(result.statusCode()).isEqualTo(500);
    assertThat(result.errorMessage()).isNull();
  }

  // ---------- Test 4: HTTP blocked when allowHttp=false ----------

  @Test
  void dispatch_blocksHttpWhenAllowHttpFalse() {
    HmacCallbackDispatcherAdapter strictHttps =
        new HmacCallbackDispatcherAdapter(Duration.ofSeconds(5), false, false);
    // Even though the local server is up and would 200, the scheme check must short-circuit.
    server.createContext("/never", new FixedStatusHandler(200, "should-not-reach"));
    String url = baseUrl + "/never";

    DispatchResult result =
        strictHttps.dispatch(url, "HmacSHA256", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    assertThat(result.statusCode()).isEqualTo(0);
    assertThat(result.errorMessage()).isEqualTo("http_disallowed");
  }

  // ---------- Test 5: HTTP allowed when allowHttp=true ----------

  @Test
  void dispatch_allowsHttpWhenAllowHttpTrue() {
    CountingHandler handler = new CountingHandler(200, "");
    server.createContext("/ok", handler);
    String url = baseUrl + "/ok";

    DispatchResult result =
        adapter.dispatch(url, "HmacSHA256", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(result.errorMessage()).isNull();
    assertThat(handler.count.get()).isEqualTo(1);
  }

  // ---------- Test 6: private network blocked ----------

  @Test
  void dispatch_blocksPrivateNetwork() {
    HmacCallbackDispatcherAdapter blocker =
        new HmacCallbackDispatcherAdapter(Duration.ofSeconds(5), true, true);
    // Use a fake 192.168.x.x URL. It must be rejected before any I/O is attempted.
    String url = "http://192.168.1.1:8080/cb";

    DispatchResult result =
        blocker.dispatch(url, "HmacSHA256", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    assertThat(result.statusCode()).isEqualTo(0);
    assertThat(result.errorMessage()).isEqualTo("private_network_blocked");
  }

  // ---------- Test 7: connection refused ----------

  @Test
  void dispatch_handlesConnectionRefused() throws IOException {
    // Find a free port and immediately release it — anything that subsequently binds here is
    // vanishingly unlikely, so a connect to it should be refused.
    int freePort;
    try (ServerSocket s = new ServerSocket(0)) {
      freePort = s.getLocalPort();
    }
    String url = "http://127.0.0.1:" + freePort + "/cb";

    DispatchResult result =
        adapter.dispatch(url, "HmacSHA256", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    assertThat(result.statusCode()).isEqualTo(0);
    assertThat(result.errorMessage()).isNotNull();
    // The contract puts the exception's simple-name in the error message.
    assertThat(result.errorMessage()).contains("ConnectException");
  }

  // ---------- Test 8: sha256 signature prefix ----------

  @Test
  void dispatch_signsBodyWithHmac_sha256Prefix() throws Exception {
    CapturingHandler handler = new CapturingHandler(200, "");
    server.createContext("/cb", handler);
    String url = baseUrl + "/cb";

    DispatchResult result =
        adapter.dispatch(url, "HmacSHA256", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(handler.latch.await(2, TimeUnit.SECONDS)).isTrue();
    String signature = handler.captured.get().headers.get("X-Signature").get(0);
    assertThat(signature).startsWith("sha256=");
  }

  // ---------- Test 9: sha512 signature prefix ----------

  @Test
  void dispatch_signsBodyWithHmac_sha512Prefix() throws Exception {
    CapturingHandler handler = new CapturingHandler(200, "");
    server.createContext("/cb", handler);
    String url = baseUrl + "/cb";

    DispatchResult result =
        adapter.dispatch(url, "HmacSHA512", SECRET, BODY, JOB_ID, DELIVERY_ID, 1);

    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(handler.latch.await(2, TimeUnit.SECONDS)).isTrue();
    String signature = handler.captured.get().headers.get("X-Signature").get(0);
    assertThat(signature).startsWith("sha512=");
  }

  // ---------- helpers ----------

  /** Captures the first incoming request, then replies with the given status. */
  private static final class CapturingHandler implements HttpHandler {
    final AtomicReference<CapturedRequest> captured = new AtomicReference<>();
    final CountDownLatch latch = new CountDownLatch(1);
    private final int statusCode;

    CapturingHandler(int statusCode, String responseBody) {
      this.statusCode = statusCode;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try {
        byte[] body = exchange.getRequestBody().readAllBytes();
        Map<String, List<String>> headers = exchange.getRequestHeaders();
        captured.set(new CapturedRequest(exchange.getRequestMethod(), headers, body));
        byte[] resp = new byte[0];
        exchange.sendResponseHeaders(statusCode, resp.length == 0 ? -1 : resp.length);
        if (resp.length > 0) {
          exchange.getResponseBody().write(resp);
        }
      } finally {
        exchange.close();
        latch.countDown();
      }
    }
  }

  private record CapturedRequest(String method, Map<String, List<String>> headers, byte[] body) {}

  /** Replies with a fixed status code and empty body, ignores the request. */
  private static final class FixedStatusHandler implements HttpHandler {
    private final int statusCode;

    @SuppressWarnings("unused")
    private final String responseBody;

    FixedStatusHandler(int statusCode, String responseBody) {
      this.statusCode = statusCode;
      this.responseBody = responseBody;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try {
        exchange.sendResponseHeaders(statusCode, -1);
      } finally {
        exchange.close();
      }
    }
  }

  /** Counts how many requests arrive; always replies with the given status. */
  private static final class CountingHandler implements HttpHandler {
    final AtomicInteger count = new AtomicInteger();
    private final int statusCode;

    CountingHandler(int statusCode, String responseBody) {
      this.statusCode = statusCode;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
      try {
        count.incrementAndGet();
        exchange.sendResponseHeaders(statusCode, -1);
      } finally {
        exchange.close();
      }
    }
  }
}

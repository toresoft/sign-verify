package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.sentry.Hint;
import io.sentry.SentryEvent;
import io.sentry.SentryOptions;
import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.exception.AppException;

class SentryConfigurationTest {

  private SentryOptions.BeforeSendCallback beforeSend() {
    SentryOptions options = new SentryOptions();
    new SentryConfiguration().sentryAppExceptionStatusFilter().configure(options);
    return options.getBeforeSend();
  }

  private SentryEvent process(Throwable t) {
    return beforeSend().execute(new SentryEvent(t), new Hint());
  }

  @Test
  void serverErrorAppException_isReported() {
    assertThat(process(AppException.dssUnavailable("dss down"))).isNotNull();
    assertThat(process(AppException.tslNotReady("tsl not ready"))).isNotNull();
    assertThat(process(AppException.concurrency("overloaded"))).isNotNull();
  }

  @Test
  void clientErrorAppException_isDropped() {
    assertThat(process(AppException.notFound("missing"))).isNull();
    assertThat(process(AppException.badRequest("bad"))).isNull();
    assertThat(process(AppException.tooLarge("too big"))).isNull();
  }

  @Test
  void wrappedClientErrorAppException_isDropped() {
    Throwable wrapped = new RuntimeException("boom", AppException.badRequest("bad"));
    assertThat(process(wrapped)).isNull();
  }

  @Test
  void nonAppException_isReported() {
    assertThat(process(new RuntimeException("unexpected"))).isNotNull();
  }
}

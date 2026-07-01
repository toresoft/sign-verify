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

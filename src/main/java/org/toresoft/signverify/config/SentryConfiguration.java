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

import io.sentry.Sentry;
import io.sentry.SentryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.toresoft.signverify.domain.exception.AppException;

/**
 * Sentry customization.
 *
 * <p>{@link AppException} cannot be blanket-ignored via {@code ignored-exceptions-for-type}: it
 * carries an HTTP status and its 5xx factories (dss/tsl unavailable, concurrency, backpressure) are
 * exactly the availability incidents that must reach Sentry. Instead we filter by status here —
 * expected client errors (&lt; 500) are dropped, server errors (&ge; 500) are reported.
 */
@Configuration
public class SentryConfiguration {

  /**
   * Below this HTTP status an {@link AppException} is an expected client error, not an incident.
   */
  static final int MIN_REPORTABLE_STATUS = 500;

  @Bean
  Sentry.OptionsConfiguration<SentryOptions> sentryAppExceptionStatusFilter() {
    return options ->
        options.setBeforeSend(
            (event, hint) -> {
              AppException app = findAppException(event.getThrowable());
              if (app != null && app.getStatus() < MIN_REPORTABLE_STATUS) {
                return null; // expected 4xx — drop so it does not create a Sentry issue
              }
              return event;
            });
  }

  /** Walks the cause chain so a wrapped {@link AppException} is still recognized. */
  static AppException findAppException(Throwable throwable) {
    for (Throwable t = throwable; t != null && t != t.getCause(); t = t.getCause()) {
      if (t instanceof AppException app) {
        return app;
      }
    }
    return null;
  }
}

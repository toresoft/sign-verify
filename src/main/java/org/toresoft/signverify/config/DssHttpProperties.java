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

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Timeouts applied to every DSS HTTP fetch (OCSP/CRL/AIA revocation and TSL retrieval). */
@ConfigurationProperties("app.dss.http")
public class DssHttpProperties {

  /** TCP connect timeout. */
  private Duration connectTimeout = Duration.ofSeconds(3);

  /** Time to lease a connection from the pool. */
  private Duration connectionRequestTimeout = Duration.ofSeconds(3);

  /** Time to receive the response. */
  private Duration responseTimeout = Duration.ofSeconds(10);

  /** Socket inactivity (read) timeout. */
  private Duration socketTimeout = Duration.ofSeconds(10);

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration connectTimeout) {
    this.connectTimeout = connectTimeout;
  }

  public Duration getConnectionRequestTimeout() {
    return connectionRequestTimeout;
  }

  public void setConnectionRequestTimeout(Duration connectionRequestTimeout) {
    this.connectionRequestTimeout = connectionRequestTimeout;
  }

  public Duration getResponseTimeout() {
    return responseTimeout;
  }

  public void setResponseTimeout(Duration responseTimeout) {
    this.responseTimeout = responseTimeout;
  }

  public Duration getSocketTimeout() {
    return socketTimeout;
  }

  public void setSocketTimeout(Duration socketTimeout) {
    this.socketTimeout = socketTimeout;
  }
}

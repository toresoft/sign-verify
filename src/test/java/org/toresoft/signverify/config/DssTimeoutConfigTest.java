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

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DssTimeoutConfigTest {

  @Test
  void revocationDataLoader_appliesConfiguredTimeouts() {
    DssHttpProperties props = new DssHttpProperties();
    props.setConnectTimeout(Duration.ofSeconds(3));
    props.setConnectionRequestTimeout(Duration.ofSeconds(4));
    props.setResponseTimeout(Duration.ofSeconds(10));
    props.setSocketTimeout(Duration.ofSeconds(11));

    CommonsDataLoader loader = new DssConfiguration().revocationDataLoader(props);

    assertThat(loader.getTimeoutConnection()).isEqualTo(3000);
    assertThat(loader.getTimeoutConnectionRequest()).isEqualTo(4000);
    assertThat(loader.getTimeoutResponse()).isEqualTo(10000);
    assertThat(loader.getTimeoutSocket()).isEqualTo(11000);
  }
}

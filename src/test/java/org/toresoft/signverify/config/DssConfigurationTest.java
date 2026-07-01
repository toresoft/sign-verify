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

import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import org.junit.jupiter.api.Test;

/**
 * Guards the production {@link CertificateVerifier} wiring. The DSS integration tests run with an
 * offline override ({@link OfflineDssTestConfig}) for speed, which would otherwise hide a
 * regression that silently drops online revocation in production. This plain unit test pins the
 * real bean.
 */
class DssConfigurationTest {

  @Test
  void production_verifier_wires_online_revocation_and_aia() {
    CertificateVerifier cv =
        new DssConfiguration().certificateVerifier(new TrustedListsCertificateSource());

    assertThat(cv.getOcspSource()).isInstanceOf(OnlineOCSPSource.class);
    assertThat(cv.getCrlSource()).isInstanceOf(OnlineCRLSource.class);
    assertThat(cv.getAIASource()).isInstanceOf(DefaultAIASource.class);
    assertThat(cv.isRevocationFallback()).isTrue();
  }
}

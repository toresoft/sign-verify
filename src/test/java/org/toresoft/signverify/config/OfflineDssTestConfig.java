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

import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Offline {@link CertificateVerifier} override active for the {@code test} profile.
 *
 * <p>The production verifier ({@link DssConfiguration#certificateVerifier}) wires live {@code
 * OnlineOCSPSource}, {@code OnlineCRLSource} and {@code DefaultAIASource} over a real HTTP data
 * loader. Under the {@code test} profile the TSL pipeline is skipped, so there are no trust anchors
 * and every signature resolves to {@code NO_CERTIFICATE_CHAIN_FOUND}: any revocation or AIA fetch
 * is therefore wasted work that cannot change the outcome, yet across the 180+ SiVa fixtures it
 * fires one HTTP round-trip per signature — many to slow or dead endpoints hitting socket timeouts.
 * That is the dominant cost of the DSS integration tests.
 *
 * <p>This bean drops all online sources so validation runs fully offline and deterministically. It
 * is {@link Primary} so it wins by-type injection (e.g. into {@code DssValidatorAdapter}) over the
 * production bean. Tests that must exercise the real online path build the production verifier
 * directly and are tagged {@code network} (see {@code DssRevocationNetworkIT}); the production
 * wiring itself is guarded by {@code DssConfigurationTest}.
 */
@Configuration
@Profile("test")
public class OfflineDssTestConfig {

  @Bean
  @Primary
  public CertificateVerifier offlineCertificateVerifier(TrustedListsCertificateSource tsl) {
    CommonCertificateVerifier cv = new CommonCertificateVerifier();
    cv.setTrustedCertSources(tsl);
    // Zero network during validation. CommonCertificateVerifier seeds a DefaultAIASource by
    // default, so AIA issuer-certificate fetching must be disabled explicitly — it is the dominant
    // network cost across the corpus once OCSP/CRL are gone.
    cv.setAIASource(null);
    cv.setOcspSource(null);
    cv.setCrlSource(null);
    cv.setRevocationFallback(false);
    return cv;
  }
}

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

import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;
import eu.europa.esig.dss.tsl.source.TLSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

@Configuration
@EnableConfigurationProperties(TslProperties.class)
public class DssConfiguration {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(DssConfiguration.class);

  @Bean
  public TrustedListsCertificateSource trustedListsCertificateSource() {
    return new TrustedListsCertificateSource();
  }

  @Bean
  public CertificateVerifier certificateVerifier(TrustedListsCertificateSource tsl) {
    CommonCertificateVerifier cv = new CommonCertificateVerifier();
    cv.setTrustedCertSources(tsl);
    cv.setAIASource(new DefaultAIASource());
    cv.setOcspSource(new OnlineOCSPSource());
    cv.setCrlSource(new OnlineCRLSource());
    cv.setRevocationFallback(true);
    return cv;
  }

  @Bean
  public FileCacheDataLoader fileCacheDataLoader(@Value("${app.dss.cache-dir}") String cacheDir)
      throws IOException {
    Path dir = Path.of(cacheDir);
    Files.createDirectories(dir);
    FileCacheDataLoader loader = new FileCacheDataLoader();
    loader.setDataLoader(new CommonsDataLoader());
    loader.setFileCacheDirectory(dir.toFile());
    loader.setCacheExpirationTime(0); // refresh sempre online quando chiamato
    return loader;
  }

  @Bean
  public TLValidationJob tlValidationJob(
      TrustedListsCertificateSource tslSource,
      FileCacheDataLoader dataLoader,
      TslProperties props,
      ResourceLoader resourceLoader)
      throws Exception {

    TLValidationJob job = new TLValidationJob();
    job.setOnlineDataLoader(dataLoader);
    job.setOfflineDataLoader(dataLoader);
    job.setTrustedListCertificateSource(tslSource);

    List<LOTLSource> lotls = new ArrayList<>();
    List<TLSource> tls = new ArrayList<>();
    for (TslProperties.Source s : props.getSources()) {
      if ("LOTL".equalsIgnoreCase(s.getType())) {
        LOTLSource lotl = new LOTLSource();
        lotl.setUrl(s.getUrl());
        lotl.setPivotSupport(s.isPivotSupport());
        lotl.setCertificateSource(loadKeystoreSource(s, resourceLoader));
        lotls.add(lotl);
      } else {
        TLSource tl = new TLSource();
        tl.setUrl(s.getUrl());
        tls.add(tl);
      }
    }
    job.setListOfTrustedListSources(lotls.toArray(new LOTLSource[0]));
    job.setTrustedListSources(tls.toArray(new TLSource[0]));
    return job;
  }

  private CommonCertificateSource loadKeystoreSource(TslProperties.Source s, ResourceLoader rl)
      throws Exception {
    if (s.getOjKeystorePath() == null) return new CommonCertificateSource();
    String envVar = s.getOjKeystorePasswordEnv();
    String pwd = (envVar != null && !envVar.isBlank()) ? System.getenv(envVar) : null;
    if (pwd == null || pwd.isBlank()) pwd = "changeit"; // fallback per dev/test
    KeyStore ks = KeyStore.getInstance("PKCS12");
    try (var in = rl.getResource(s.getOjKeystorePath()).getInputStream()) {
      ks.load(in, pwd.toCharArray());
    }
    CommonCertificateSource src = new CommonCertificateSource();
    int loaded = 0;
    var aliases = ks.aliases();
    while (aliases.hasMoreElements()) {
      String a = aliases.nextElement();
      var cert = ks.getCertificate(a);
      if (cert instanceof java.security.cert.X509Certificate x) {
        src.addCertificate(new CertificateToken(x));
        loaded++;
      }
    }
    if (loaded == 0) {
      log.warn(
          "OJ keystore '{}' for LOTL '{}' contains no X.509 certificate: the LOTL signature cannot"
              + " be validated (pivots will fail with NO_CERTIFICATE_CHAIN_FOUND). Rebuild it with"
              + " scripts/update-oj-keystore.sh.",
          s.getOjKeystorePath(),
          s.getUrl());
    } else {
      log.info(
          "Loaded {} OJ signing certificate(s) from keystore '{}' for LOTL '{}'",
          loaded,
          s.getOjKeystorePath(),
          s.getUrl());
    }
    return src;
  }
}

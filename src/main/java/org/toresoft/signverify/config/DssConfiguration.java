package org.toresoft.signverify.config;

import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DssConfiguration {

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
}

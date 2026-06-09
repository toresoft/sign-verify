package org.toresoft.signverify.adapter.dss;

import eu.europa.esig.dss.model.tsl.TrustProperties;
import eu.europa.esig.dss.model.tsl.TrustService;
import eu.europa.esig.dss.model.tsl.TrustServiceProvider;
import eu.europa.esig.dss.model.tsl.TrustServiceStatusAndInformationExtensions;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.TrustedCertificate;
import org.toresoft.signverify.persistence.TrustedCertificateRepository;

@Component
public class TrustedCertificateMirror {

  // Maximum lengths of the bounded VARCHAR columns of trusted_certificate (see the
  // Flyway schema / TrustedCertificate entity). Scalar values extracted from a
  // certificate are truncated to these so that a single non-standard certificate
  // (e.g. a countryName longer than the spec allows) cannot overflow a column and
  // abort the whole TSL sync transaction. subject_dn / issuer_dn / tsl_url /
  // certificate_der_b64 are TEXT and need no truncation.
  private static final int LEN_SKI = 64;
  private static final int LEN_CN = 255;
  private static final int LEN_SERIAL = 80;
  private static final int LEN_COUNTRY = 8;
  private static final int LEN_TSP_NAME = 255;
  private static final int LEN_TSP_TYPE = 255;
  private static final int LEN_TSP_STATUS = 80;

  public record Diff(int added, int removed, int unchanged) {}

  private final TrustedCertificateRepository repo;

  public TrustedCertificateMirror(TrustedCertificateRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public Diff sync(TrustedListsCertificateSource src) {
    Map<String, TrustedCertificate> dbByFp = new HashMap<>();
    repo.findAll().stream()
        .filter(c -> c.getRemovedAt() == null)
        .forEach(c -> dbByFp.put(c.getFingerprintSha256(), c));

    Set<String> currentFp = new HashSet<>();
    int added = 0;
    int unchanged = 0;
    Instant now = Instant.now();

    for (CertificateToken token : src.getCertificates()) {
      String fp = fingerprint(token);
      currentFp.add(fp);
      TrustedCertificate existing = dbByFp.get(fp);
      if (existing != null) {
        existing.setLastSeenAt(now);
        repo.save(existing);
        unchanged++;
      } else {
        TrustedCertificate tc = newEntity(token, src, fp, now);
        repo.save(tc);
        added++;
      }
    }

    int removed = 0;
    for (var e : dbByFp.entrySet()) {
      if (!currentFp.contains(e.getKey())) {
        e.getValue().setRemovedAt(now);
        repo.save(e.getValue());
        removed++;
      }
    }
    return new Diff(added, removed, unchanged);
  }

  private TrustedCertificate newEntity(
      CertificateToken t, TrustedListsCertificateSource src, String fp, Instant now) {
    TrustedCertificate c = new TrustedCertificate();
    c.setId(UUID.randomUUID());
    c.setFingerprintSha256(fp);
    c.setSki(trunc(extractSki(t), LEN_SKI));
    c.setSubjectDn(t.getSubject().getRFC2253()); // TEXT column, no limit
    c.setSubjectCn(trunc(extractCn(t.getSubject().getRFC2253()), LEN_CN));
    c.setIssuerDn(t.getIssuer().getRFC2253()); // TEXT column, no limit
    c.setIssuerCn(trunc(extractCn(t.getIssuer().getRFC2253()), LEN_CN));
    c.setSerialNumber(trunc(t.getSerialNumber().toString(16), LEN_SERIAL));
    c.setCountry(trunc(extractCountry(t.getSubject().getRFC2253()), LEN_COUNTRY));
    c.setValidFrom(t.getNotBefore().toInstant());
    c.setValidTo(t.getNotAfter().toInstant());
    c.setCertificateDerB64(Base64.getEncoder().encodeToString(t.getEncoded()));
    c.setLastSeenAt(now);

    // TSP metadata: trova la prima trust service per questo cert
    List<TrustProperties> trustProps = src.getTrustServices(t);
    if (!trustProps.isEmpty()) {
      TrustServiceProvider tsp = trustProps.get(0).getTrustServiceProvider();
      if (tsp != null) {
        c.setTspName(
            trunc(
                tsp.getNames().values().stream()
                    .findFirst()
                    .flatMap(l -> l.stream().findFirst())
                    .orElse(null),
                LEN_TSP_NAME));
        List<TrustService> services =
            tsp.getServices().stream().filter(s -> s.getCertificates().contains(t)).toList();
        if (!services.isEmpty()) {
          TrustServiceStatusAndInformationExtensions status =
              services.get(0).getStatusAndInformationExtensions().getLatest();
          c.setTspServiceType(trunc(status.getType(), LEN_TSP_TYPE));
          c.setTspServiceStatus(trunc(status.getStatus(), LEN_TSP_STATUS));
        }
      }
    }
    return c;
  }

  private String extractSki(CertificateToken t) {
    // Subject Key Identifier OID: 2.5.29.14
    byte[] ext = t.getCertificate().getExtensionValue("2.5.29.14");
    if (ext == null) return null;
    // Extension value is wrapped in ASN.1 OCTET STRING — skip wrapper bytes
    if (ext.length < 4) return toHex(ext);
    int len = ext[3] & 0xFF;
    if (ext.length < 4 + len) return toHex(ext);
    byte[] ski = new byte[len];
    System.arraycopy(ext, 4, ski, 0, len);
    return toHex(ski);
  }

  private String fingerprint(CertificateToken t) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return toHex(md.digest(t.getEncoded()));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String toHex(byte[] bytes) {
    if (bytes == null) return null;
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  private String extractCn(String dn) {
    for (String part : dn.split(",")) {
      String t = part.trim();
      if (t.toUpperCase(Locale.ROOT).startsWith("CN=")) return t.substring(3);
    }
    return null;
  }

  private String extractCountry(String dn) {
    for (String part : dn.split(",")) {
      String t = part.trim();
      if (t.toUpperCase(Locale.ROOT).startsWith("C=")) return t.substring(2);
    }
    return null;
  }

  /** Truncates {@code s} to at most {@code max} characters (null-safe). */
  private static String trunc(String s, int max) {
    if (s == null || s.length() <= max) return s;
    return s.substring(0, max);
  }
}

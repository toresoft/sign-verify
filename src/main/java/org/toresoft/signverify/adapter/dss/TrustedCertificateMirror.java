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
package org.toresoft.signverify.adapter.dss;

import eu.europa.esig.dss.model.tsl.TrustProperties;
import eu.europa.esig.dss.model.tsl.TrustService;
import eu.europa.esig.dss.model.tsl.TrustServiceProvider;
import eu.europa.esig.dss.model.tsl.TrustServiceStatusAndInformationExtensions;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.X500PrincipalHelper;
import eu.europa.esig.dss.model.x509.extension.SubjectKeyIdentifier;
import eu.europa.esig.dss.spi.CertificateExtensionsUtils;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1String;
import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x500.style.IETFUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.TrustedCertificate;
import org.toresoft.signverify.domain.port.TrustedListMirrorPort;
import org.toresoft.signverify.persistence.TrustedCertificateRepository;

@Component
public class TrustedCertificateMirror implements TrustedListMirrorPort {

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

  // A full EU TSL sync handles a few thousand certificates; new entities are persisted in
  // chunks with flush/clear so the persistence context never holds the whole set, and
  // already-known rows are touched via bulk updates without loading them at all.
  private static final int CHUNK_SIZE = 500;

  private final TrustedCertificateRepository repo;
  private final EntityManager em;
  private final TrustedListsCertificateSource src;

  public TrustedCertificateMirror(
      TrustedCertificateRepository repo, EntityManager em, TrustedListsCertificateSource src) {
    this.repo = repo;
    this.em = em;
    this.src = src;
  }

  @Override
  @Transactional
  public Diff sync() {
    Set<String> dbFps = new HashSet<>(repo.findActiveFingerprints());

    Set<String> currentFps = new HashSet<>();
    List<String> toTouch = new ArrayList<>();
    List<TrustedCertificate> insertBuffer = new ArrayList<>(CHUNK_SIZE);
    int added = 0;
    Instant now = Instant.now();

    for (CertificateToken token : src.getCertificates()) {
      String fp = fingerprint(token);
      if (!currentFps.add(fp)) {
        continue; // same certificate listed by more than one trust service
      }
      if (dbFps.contains(fp)) {
        toTouch.add(fp);
      } else {
        insertBuffer.add(newEntity(token, src, fp, now));
        if (insertBuffer.size() >= CHUNK_SIZE) {
          added += persistChunk(insertBuffer);
        }
      }
    }
    added += persistChunk(insertBuffer);

    for (List<String> chunk : chunks(toTouch)) {
      repo.touchLastSeen(now, chunk);
    }

    List<String> toRemove = dbFps.stream().filter(fp -> !currentFps.contains(fp)).toList();
    int removed = 0;
    for (List<String> chunk : chunks(toRemove)) {
      removed += repo.markRemoved(now, chunk);
    }
    return new Diff(added, removed, toTouch.size());
  }

  /** Persists and clears the buffer, detaching the chunk from the persistence context. */
  private int persistChunk(List<TrustedCertificate> buffer) {
    if (buffer.isEmpty()) {
      return 0;
    }
    int n = buffer.size();
    repo.saveAll(buffer);
    repo.flush();
    em.clear();
    buffer.clear();
    return n;
  }

  private static List<List<String>> chunks(List<String> list) {
    List<List<String>> out = new ArrayList<>();
    for (int i = 0; i < list.size(); i += CHUNK_SIZE) {
      out.add(list.subList(i, Math.min(i + CHUNK_SIZE, list.size())));
    }
    return out;
  }

  private TrustedCertificate newEntity(
      CertificateToken t, TrustedListsCertificateSource src, String fp, Instant now) {
    TrustedCertificate c = new TrustedCertificate();
    c.setId(UUID.randomUUID());
    c.setFingerprintSha256(fp);
    c.setSki(trunc(extractSki(t), LEN_SKI));
    c.setSubjectDn(t.getSubject().getRFC2253()); // TEXT column, no limit
    c.setSubjectCn(trunc(firstRdnValue(t.getSubject(), BCStyle.CN), LEN_CN));
    c.setIssuerDn(t.getIssuer().getRFC2253()); // TEXT column, no limit
    c.setIssuerCn(trunc(firstRdnValue(t.getIssuer(), BCStyle.CN), LEN_CN));
    c.setSerialNumber(trunc(t.getSerialNumber().toString(16), LEN_SERIAL));
    c.setCountry(trunc(firstRdnValue(t.getSubject(), BCStyle.C), LEN_COUNTRY));
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
    SubjectKeyIdentifier ski = CertificateExtensionsUtils.getSubjectKeyIdentifier(t);
    return ski == null ? null : toHex(ski.getSki());
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

  /**
   * Extracts the first value of the given attribute (e.g. CN, C) from a DN, parsing the DER
   * encoding with BouncyCastle instead of splitting the RFC2253 string, which would break on
   * escaped commas and multi-valued RDNs.
   */
  private String firstRdnValue(X500PrincipalHelper principal, ASN1ObjectIdentifier oid) {
    X500Name name = X500Name.getInstance(principal.getEncoded());
    for (RDN rdn : name.getRDNs(oid)) {
      for (AttributeTypeAndValue atv : rdn.getTypesAndValues()) {
        if (oid.equals(atv.getType())) {
          return atv.getValue() instanceof ASN1String s
              ? s.getString()
              : IETFUtils.valueToString(atv.getValue());
        }
      }
    }
    return null;
  }

  /** Truncates {@code s} to at most {@code max} characters (null-safe). */
  private static String trunc(String s, int max) {
    if (s == null || s.length() <= max) return s;
    return s.substring(0, max);
  }
}

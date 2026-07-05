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

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.spi.signature.AdvancedSignature;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import java.util.List;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;

/**
 * Single-level unwrap of a DSS-supported signed container. Returns the child documents as DSS
 * {@link DSSDocument}s (lazily streamable) so the recursion driver can spill leaves to disk instead
 * of reading them into heap. Every parse failure surfaces as {@link AppException} so the driver can
 * tell a genuine leaf/service error apart from a non-container.
 */
@Component
public class DssExtractionAdapter {

  private final CertificateVerifier certificateVerifier;

  public DssExtractionAdapter(CertificateVerifier certificateVerifier) {
    this.certificateVerifier = certificateVerifier;
  }

  /** Result of a single unwrap: outer signature form plus its original documents. */
  public record DssParse(String format, List<DSSDocument> originals) {}

  public DssParse parse(DSSDocument doc) {
    SignedDocumentValidator validator;
    try {
      validator = SignedDocumentValidator.fromDocument(doc);
    } catch (Exception e) {
      throw AppException.signatureParseError("cannot parse signed document: " + e.getMessage());
    }
    validator.setCertificateVerifier(certificateVerifier);

    var signatures = getSignaturesOrThrow(validator);
    if (signatures.isEmpty()) {
      throw AppException.signatureParseError("no signatures found");
    }
    var firstSignature = signatures.get(0);
    String firstSigId = firstSignature.getId();

    List<DSSDocument> originals;
    try {
      originals = validator.getOriginalDocuments(firstSigId);
    } catch (Exception e) {
      throw AppException.badRequest("cannot extract originals: " + e.getMessage());
    }

    String format;
    try {
      format = firstSignature.getSignatureForm().name();
    } catch (Exception e) {
      format = "UNKNOWN";
    }
    return new DssParse(format, originals);
  }

  /**
   * {@code validator.getSignatures()} can itself throw a raw DSS/format exception (e.g. a malformed
   * PDF byte-range slice recursed back in as a fresh document) rather than merely returning an
   * empty list. Callers of this adapter (notably {@link RecursiveExtractionAdapter}) rely on every
   * parse failure surfacing as {@link AppException} so it can be told apart from a genuine
   * leaf/service failure — so any exception here is translated too.
   */
  private List<AdvancedSignature> getSignaturesOrThrow(SignedDocumentValidator validator) {
    try {
      return validator.getSignatures();
    } catch (Exception e) {
      throw AppException.signatureParseError("cannot parse signed document: " + e.getMessage());
    }
  }
}

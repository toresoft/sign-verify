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

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.policy.ValidationPolicy;
import eu.europa.esig.dss.policy.EtsiValidationPolicyFactory;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationRequest;
import org.toresoft.signverify.domain.port.ValidationResult;

@Component
public class DssValidatorAdapter implements SignatureValidatorPort {

  private final CertificateVerifier certificateVerifier;
  private final ObjectMapper om;

  public DssValidatorAdapter(CertificateVerifier certificateVerifier, ObjectMapper om) {
    this.certificateVerifier = certificateVerifier;
    this.om = om;
  }

  @Override
  @CircuitBreaker(name = "dssValidator", fallbackMethod = "fallback")
  public ValidationResult validate(ValidationRequest req) {
    var doc = new InMemoryDocument(req.documentBytes(), req.filename());
    SignedDocumentValidator validator;
    try {
      validator = SignedDocumentValidator.fromDocument(doc);
    } catch (Exception e) {
      throw AppException.signatureParseError("cannot parse signed document: " + e.getMessage());
    }
    validator.setCertificateVerifier(certificateVerifier);

    ValidationPolicy policy = loadPolicy(req.policyXml());
    Reports reports = validator.validateDocument(policy);
    SimpleReport simple = reports.getSimpleReport();

    Map<ReportType, String> out = serialize(req.reports(), reports, om);

    // For multi-signature documents the top-level outcome reflects the worst signature, so a single
    // invalid signature can never be masked by a passing one appearing first.
    String reportingId = worstSignatureId(simple);
    String format =
        reportingId != null && simple.getSignatureFormat(reportingId) != null
            ? simple.getSignatureFormat(reportingId).toString()
            : "UNKNOWN";
    String indication =
        reportingId != null && simple.getIndication(reportingId) != null
            ? simple.getIndication(reportingId).toString()
            : "INDETERMINATE";
    String subIndication =
        reportingId != null && simple.getSubIndication(reportingId) != null
            ? simple.getSubIndication(reportingId).toString()
            : null;
    return new ValidationResult(
        format,
        indication,
        subIndication,
        simple.getSignaturesCount(),
        out,
        SimpleReportMapper.signatures(simple),
        SimpleReportMapper.timestamps(simple));
  }

  /**
   * Selects the signature with the worst (least favourable) indication for the top-level outcome.
   */
  private String worstSignatureId(SimpleReport simple) {
    String worstId = simple.getFirstSignatureId();
    int worstRank = -1;
    for (String id : simple.getSignatureIdList()) {
      int rank = indicationRank(simple.getIndication(id));
      if (rank > worstRank) {
        worstRank = rank;
        worstId = id;
      }
    }
    return worstId;
  }

  static int indicationRank(Indication indication) {
    if (indication == null) return 2;
    return switch (indication) {
      case TOTAL_FAILED, FAILED -> 3;
      case INDETERMINATE -> 2;
      case TOTAL_PASSED, PASSED -> 0;
      default -> 1;
    };
  }

  static ValidationPolicy loadPolicy(String policyXml) {
    try {
      var policyDoc =
          new InMemoryDocument(policyXml.getBytes(StandardCharsets.UTF_8), "validation-policy.xml");
      return new EtsiValidationPolicyFactory().loadValidationPolicy(policyDoc);
    } catch (Exception e) {
      throw AppException.badRequest("invalid validation policy: " + e.getMessage());
    }
  }

  static Map<ReportType, String> serialize(
      Set<ReportType> wanted, Reports reports, ObjectMapper om) {
    Map<ReportType, String> out = new EnumMap<>(ReportType.class);
    try {
      if (wanted.contains(ReportType.SIMPLE))
        out.put(ReportType.SIMPLE, om.writeValueAsString(reports.getSimpleReportJaxb()));
      if (wanted.contains(ReportType.DETAILED))
        out.put(ReportType.DETAILED, om.writeValueAsString(reports.getDetailedReportJaxb()));
      if (wanted.contains(ReportType.DIAGNOSTIC))
        out.put(ReportType.DIAGNOSTIC, om.writeValueAsString(reports.getDiagnosticDataJaxb()));
      if (wanted.contains(ReportType.ETSI))
        out.put(ReportType.ETSI, om.writeValueAsString(reports.getEtsiValidationReportJaxb()));
    } catch (Exception e) {
      throw new IllegalStateException("report serialization", e);
    }
    return out;
  }

  public ValidationResult fallback(ValidationRequest req, Throwable t) {
    if (t instanceof AppException) throw (AppException) t;
    throw AppException.dssUnavailable("dss circuit breaker open: " + t.getMessage());
  }
}

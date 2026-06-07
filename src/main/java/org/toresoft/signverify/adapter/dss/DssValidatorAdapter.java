package org.toresoft.signverify.adapter.dss;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.policy.ValidationPolicy;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.policy.ValidationPolicyLoader;
import eu.europa.esig.dss.validation.reports.Reports;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.Map;
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

    ValidationPolicy policy;
    try {
      policy =
          ValidationPolicyLoader.fromValidationPolicy(
                  new ByteArrayInputStream(req.policyXml().getBytes()))
              .create();
    } catch (Exception e) {
      throw AppException.badRequest("invalid validation policy: " + e.getMessage());
    }

    Reports reports = validator.validateDocument(policy);
    SimpleReport simple = reports.getSimpleReport();

    Map<ReportType, String> out = new EnumMap<>(ReportType.class);
    try {
      if (req.reports().contains(ReportType.SIMPLE))
        out.put(ReportType.SIMPLE, om.writeValueAsString(reports.getSimpleReportJaxb()));
      if (req.reports().contains(ReportType.DETAILED))
        out.put(ReportType.DETAILED, om.writeValueAsString(reports.getDetailedReportJaxb()));
      if (req.reports().contains(ReportType.DIAGNOSTIC))
        out.put(ReportType.DIAGNOSTIC, om.writeValueAsString(reports.getDiagnosticDataJaxb()));
      if (req.reports().contains(ReportType.ETSI))
        out.put(ReportType.ETSI, om.writeValueAsString(reports.getEtsiValidationReportJaxb()));
    } catch (Exception e) {
      throw new IllegalStateException("report serialization", e);
    }

    String firstId = simple.getFirstSignatureId();
    String format =
        firstId != null && simple.getSignatureFormat(firstId) != null
            ? simple.getSignatureFormat(firstId).toString()
            : "UNKNOWN";
    String indication =
        firstId != null && simple.getIndication(firstId) != null
            ? simple.getIndication(firstId).toString()
            : "INDETERMINATE";
    String subIndication =
        firstId != null && simple.getSubIndication(firstId) != null
            ? simple.getSubIndication(firstId).toString()
            : null;
    return new ValidationResult(
        format, indication, subIndication, simple.getSignaturesCount(), out);
  }

  public ValidationResult fallback(ValidationRequest req, Throwable t) {
    if (t instanceof AppException) throw (AppException) t;
    throw AppException.dssUnavailable("dss circuit breaker open: " + t.getMessage());
  }
}

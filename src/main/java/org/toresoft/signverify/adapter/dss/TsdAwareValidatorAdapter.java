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
import eu.europa.esig.dss.validation.timestamp.DetachedTimestampValidator;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.cms.CMSTimeStampedData;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.exception.Errors;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.SignatureValidatorPort;
import org.toresoft.signverify.domain.port.ValidationRequest;
import org.toresoft.signverify.domain.port.ValidationResult;

/**
 * Adds RFC 5544 TimeStampedData (TSD) support in front of {@link DssValidatorAdapter}.
 *
 * <p>DSS 6.4 has no factory for {@code id-aa-timeStampedData}: {@code
 * SignedDocumentValidator.fromDocument()} throws {@code IllegalInputException} for a {@code .tsd},
 * which the delegate surfaces as {@link Errors#SIGNATURE_PARSE_ERROR}. This decorator intercepts
 * that single failure and retries through Bouncy Castle: it unwraps the inner document and
 * validates both the (optional) inner CAdES signatures and the wrapper RFC 3161 timestamps, then
 * aggregates the outcome with strict worst-of semantics — any {@code INDETERMINATE} element lowers
 * the overall result to {@code INDETERMINATE}, any {@code FAILED} element to {@code FAILED}.
 *
 * <p>The CAdES/PAdES/XAdES/JAdES/ASiC path stays untouched: anything the delegate parses is
 * returned verbatim, and a payload that is neither a signed document nor a TSD keeps the original
 * parse error.
 */
@Component
@Primary
public class TsdAwareValidatorAdapter implements SignatureValidatorPort {

  static final String TSD_FORMAT = "RFC5544_TSD";

  private final DssValidatorAdapter delegate;
  private final CertificateVerifier certificateVerifier;
  private final ObjectMapper om;

  public TsdAwareValidatorAdapter(
      DssValidatorAdapter delegate, CertificateVerifier certificateVerifier, ObjectMapper om) {
    this.delegate = delegate;
    this.certificateVerifier = certificateVerifier;
    this.om = om;
  }

  @Override
  public ValidationResult validate(ValidationRequest req) {
    try {
      return delegate.validate(req);
    } catch (AppException e) {
      if (!Errors.SIGNATURE_PARSE_ERROR.equals(e.getCode())) throw e;
      return validateTsd(req, e);
    }
  }

  private ValidationResult validateTsd(ValidationRequest req, AppException original) {
    CMSTimeStampedData tsd;
    try {
      tsd = new CMSTimeStampedData(req.documentBytes());
    } catch (Exception notTsd) {
      // Not a signed document and not a TSD either: keep the original CAdES parse error.
      throw original;
    }

    byte[] content = tsd.getContent();
    if (content == null || content.length == 0) {
      throw AppException.signatureParseError(
          "TSD wrapper without inner content (dataUri-only TSD is not supported)");
    }

    ValidationPolicy policy = loadPolicy(req.policyXml());
    List<Element> elements = new ArrayList<>();
    List<org.toresoft.signverify.domain.port.SignatureSummary> sigSummaries = new ArrayList<>();
    List<org.toresoft.signverify.domain.port.TimestampSummary> tsSummaries = new ArrayList<>();
    Reports primaryReports = null;

    // 1) The inner content may itself be a CAdES signature (TSD over a signed .p7m).
    Reports innerReports = tryValidateInnerSignatures(content, policy);
    int innerSignatureCount = 0;
    String innerFormat = null;
    if (innerReports != null) {
      SimpleReport sr = innerReports.getSimpleReport();
      innerSignatureCount = sr.getSignaturesCount();
      for (String id : sr.getSignatureIdList()) {
        elements.add(
            new Element(
                rank(sr.getIndication(id)),
                str(sr.getIndication(id)),
                str(sr.getSubIndication(id))));
        if (innerFormat == null && sr.getSignatureFormat(id) != null) {
          innerFormat = sr.getSignatureFormat(id).toString();
        }
      }
      sigSummaries.addAll(SimpleReportMapper.signatures(sr));
      primaryReports = innerReports;
    }

    // 2) Validate every RFC 3161 timestamp carried by the TSD wrapper.
    TimeStampToken[] tokens;
    try {
      tokens = tsd.getTimeStampTokens();
    } catch (Exception e) {
      throw AppException.signatureParseError(
          "cannot read TSD timestamp evidence: " + e.getMessage());
    }
    for (TimeStampToken token : tokens) {
      Reports tsReports = validateWrapperTimestamp(token, content, policy);
      SimpleReport sr = tsReports.getSimpleReport();
      for (String id : sr.getTimestampIdList()) {
        elements.add(
            new Element(
                rank(sr.getIndication(id)),
                str(sr.getIndication(id)),
                str(sr.getSubIndication(id))));
      }
      if (primaryReports == null) primaryReports = tsReports;
      tsSummaries.addAll(SimpleReportMapper.timestamps(sr));
    }

    if (elements.isEmpty() || primaryReports == null) {
      throw AppException.signatureParseError("TSD without inner signatures or timestamp tokens");
    }

    Element worst = elements.get(0);
    for (Element el : elements) {
      if (el.rank() > worst.rank()) worst = el;
    }

    String format = innerFormat != null ? innerFormat : TSD_FORMAT;
    Map<ReportType, String> out = serialize(req.reports(), primaryReports);
    return new ValidationResult(
        format,
        worst.indication(),
        worst.subIndication(),
        innerSignatureCount,
        out,
        sigSummaries,
        tsSummaries);
  }

  private Reports tryValidateInnerSignatures(byte[] content, ValidationPolicy policy) {
    try {
      var innerDoc = new InMemoryDocument(content, "tsd-inner");
      SignedDocumentValidator v = SignedDocumentValidator.fromDocument(innerDoc);
      v.setCertificateVerifier(certificateVerifier);
      return v.validateDocument(policy);
    } catch (Exception notCades) {
      // Inner content is plain (timestamped data only), not a signed document.
      return null;
    }
  }

  private Reports validateWrapperTimestamp(
      TimeStampToken token, byte[] content, ValidationPolicy policy) {
    byte[] tokenBytes;
    try {
      tokenBytes = token.getEncoded();
    } catch (Exception e) {
      throw AppException.signatureParseError(
          "cannot encode TSD timestamp token: " + e.getMessage());
    }
    byte[] timestamped = resolveTimestampedContent(content, token);
    var dtv = new DetachedTimestampValidator(new InMemoryDocument(tokenBytes));
    dtv.setTimestampedData(new InMemoryDocument(timestamped));
    dtv.setCertificateVerifier(certificateVerifier);
    return dtv.validateDocument(policy);
  }

  /**
   * Picks the bytes the RFC 3161 token actually covers. The common PA case (ArubaSign/GoSign) marks
   * the {@code content} field directly, so {@link CMSTimeStampedData#getContent()} matches. Some
   * producers (e.g. {@code Crypt::TimestampedData}) wrap the payload in a CMS {@code id-data}
   * ContentInfo and timestamp the inner octets instead; in that case the wrapper is peeled one
   * layer. If nothing matches the imprint, the raw content is returned so DSS reports an honest
   * {@code HASH_FAILURE}.
   */
  private byte[] resolveTimestampedContent(byte[] content, TimeStampToken token) {
    if (imprintMatches(content, token)) return content;
    try {
      var ci = ContentInfo.getInstance(ASN1Primitive.fromByteArray(content));
      if (ci.getContent() instanceof ASN1OctetString inner) {
        byte[] innerOctets = inner.getOctets();
        if (imprintMatches(innerOctets, token)) return innerOctets;
      }
    } catch (Exception notCms) {
      // content is not a CMS ContentInfo wrapper — fall through to the raw content.
    }
    return content;
  }

  private boolean imprintMatches(byte[] candidate, TimeStampToken token) {
    try {
      var info = token.getTimeStampInfo();
      DigestCalculator dc = new BcDigestCalculatorProvider().get(info.getHashAlgorithm());
      dc.getOutputStream().write(candidate);
      dc.getOutputStream().close();
      return Arrays.equals(dc.getDigest(), info.getMessageImprintDigest());
    } catch (Exception e) {
      return false;
    }
  }

  private ValidationPolicy loadPolicy(String policyXml) {
    try {
      var policyDoc =
          new InMemoryDocument(policyXml.getBytes(StandardCharsets.UTF_8), "validation-policy.xml");
      return new EtsiValidationPolicyFactory().loadValidationPolicy(policyDoc);
    } catch (Exception e) {
      throw AppException.badRequest("invalid validation policy: " + e.getMessage());
    }
  }

  private Map<ReportType, String> serialize(Set<ReportType> wanted, Reports reports) {
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

  /** Worst-of aggregation: higher rank = less favourable outcome. */
  private static int rank(Indication indication) {
    if (indication == null) return 2;
    return switch (indication) {
      case TOTAL_FAILED, FAILED -> 3;
      case INDETERMINATE -> 2;
      case TOTAL_PASSED, PASSED -> 0;
      default -> 1;
    };
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private record Element(int rank, String indication, String subIndication) {}
}

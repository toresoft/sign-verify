package org.toresoft.signverify.adapter.dss;

import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.simplereport.jaxb.XmlEvidenceRecord;
import eu.europa.esig.dss.simplereport.jaxb.XmlTimestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.toresoft.signverify.domain.port.CertificateSummary;
import org.toresoft.signverify.domain.port.SignatureSummary;
import org.toresoft.signverify.domain.port.TimestampSummary;

/**
 * Maps a DSS {@link SimpleReport} into transport-friendly per-signature / per-timestamp summaries.
 */
final class SimpleReportMapper {

  private SimpleReportMapper() {}

  static List<SignatureSummary> signatures(SimpleReport report) {
    List<SignatureSummary> out = new ArrayList<>();
    for (String id : report.getSignatureIdList()) {
      out.add(
          new SignatureSummary(
              id,
              str(report.getIndication(id)),
              str(report.getSubIndication(id)),
              str(report.getSignatureFormat(id)),
              qualification(report, id),
              report.getSignedBy(id),
              odt(report.getBestSignatureTime(id)),
              odt(report.getSigningTime(id)),
              archiveTimestamps(report, id),
              certificates(report, id),
              signatureTimestamps(report, id)));
    }
    return out;
  }

  /** Timestamps covering a specific signature (signature/archive timestamps of that signature). */
  private static List<TimestampSummary> signatureTimestamps(SimpleReport report, String id) {
    List<TimestampSummary> out = new ArrayList<>();
    for (XmlTimestamp t : report.getSignatureTimestamps(id)) {
      out.add(
          new TimestampSummary(
              t.getId(),
              str(t.getIndication()),
              str(t.getSubIndication()),
              odt(t.getProductionTime()),
              tsLevel(t)));
    }
    return out;
  }

  /** Archive timestamps extracted from evidence records attached to a signature (LTA). */
  private static List<TimestampSummary> archiveTimestamps(SimpleReport report, String id) {
    List<TimestampSummary> out = new ArrayList<>();
    var records = report.getSignatureEvidenceRecords(id);
    if (records == null) {
      return out;
    }
    for (XmlEvidenceRecord er : records) {
      var timestamps = report.getEvidenceRecordTimestamps(er.getId());
      if (timestamps == null) {
        continue;
      }
      for (XmlTimestamp t : timestamps) {
        out.add(
            new TimestampSummary(
                t.getId(),
                str(t.getIndication()),
                str(t.getSubIndication()),
                odt(t.getProductionTime()),
                tsLevel(t)));
      }
    }
    return out;
  }

  private static String tsLevel(XmlTimestamp t) {
    var lvl = t.getTimestampLevel();
    var q = lvl == null ? null : lvl.getValue();
    return q == null ? "NA" : q.name();
  }

  static List<TimestampSummary> timestamps(SimpleReport report) {
    List<TimestampSummary> out = new ArrayList<>();
    for (String id : report.getTimestampIdList()) {
      out.add(
          new TimestampSummary(
              id,
              str(report.getIndication(id)),
              str(report.getSubIndication(id)),
              odt(report.getProductionTime(id)),
              tsQualification(report, id)));
    }
    return out;
  }

  private static String qualification(SimpleReport report, String id) {
    var q = report.getSignatureQualification(id);
    return q == null ? "NA" : q.name();
  }

  private static String tsQualification(SimpleReport report, String id) {
    var q = report.getTimestampQualification(id);
    return q == null ? "NA" : q.name();
  }

  private static String str(Object o) {
    return o == null ? null : o.toString();
  }

  private static OffsetDateTime odt(Date d) {
    return d == null ? null : d.toInstant().atOffset(ZoneOffset.UTC);
  }

  private static List<CertificateSummary> certificates(SimpleReport report, String signatureId) {
    var chain = report.getCertificateChain(signatureId);
    if (chain == null) {
      return List.of();
    }
    List<CertificateSummary> out = new ArrayList<>();
    for (var cert : chain.getCertificate()) {
      out.add(
          new CertificateSummary(cert.getId(), cert.getQualifiedName(), odt(cert.getSunsetDate())));
    }
    return out;
  }
}

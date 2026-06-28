package org.toresoft.signverify.adapter.dss;

import eu.europa.esig.dss.simplereport.SimpleReport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
              timestamps(report)));
    }
    return out;
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
}

package org.toresoft.signverify.domain.port;

import java.util.Map;

public record ValidationResult(
    String signatureFormat,
    String indication,
    String subIndication,
    int signatureCount,
    Map<ReportType, String> reportsJson) {}

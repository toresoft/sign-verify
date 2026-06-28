package org.toresoft.signverify.domain.port;

import java.time.OffsetDateTime;
import java.util.List;

public record SignatureSummary(
    String id,
    String indication,
    String subIndication,
    String signatureFormat,
    String signatureLevel,
    String signedBy,
    OffsetDateTime bestSignatureTime,
    OffsetDateTime claimedSigningTime,
    List<TimestampSummary> archiveTimestamps,
    List<CertificateSummary> certificates,
    List<TimestampSummary> timestamps) {}

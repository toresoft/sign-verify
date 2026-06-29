package org.toresoft.signverify.domain.port;

import java.time.OffsetDateTime;

public record TimestampSummary(
    String id,
    String indication,
    String subIndication,
    OffsetDateTime productionTime,
    String qualification) {}

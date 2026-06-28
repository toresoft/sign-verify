package org.toresoft.signverify.domain.port;

import java.time.OffsetDateTime;

public record CertificateSummary(String id, String qualifiedName, OffsetDateTime sunsetDate) {}

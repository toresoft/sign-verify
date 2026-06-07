package org.toresoft.signverify.domain.port;

import java.util.Set;

public record ValidationRequest(
    byte[] documentBytes, String filename, String policyXml, Set<ReportType> reports) {}

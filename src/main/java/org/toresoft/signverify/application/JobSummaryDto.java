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
package org.toresoft.signverify.application;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.toresoft.signverify.domain.model.ValidationJob;

/**
 * Summary view of a {@link ValidationJob} for the paginated jobs list. Never exposes result bodies,
 * callback secrets, or storage paths.
 */
public record JobSummaryDto(
    UUID jobId,
    String status,
    Owner requestedBy,
    OffsetDateTime createdAt,
    OffsetDateTime startedAt,
    OffsetDateTime completedAt,
    OffsetDateTime expiresAt,
    int callbackAttempts,
    String phase,
    OffsetDateTime phaseUpdatedAt) {

  /** The principal that submitted the job (its owner). */
  public record Owner(String type, String id) {}

  public static JobSummaryDto from(ValidationJob j) {
    return new JobSummaryDto(
        j.getId(),
        j.getStatus().name(),
        new Owner(j.getRequestedByPrincipalType().name(), j.getRequestedByPrincipalId()),
        utc(j.getCreatedAt()),
        utc(j.getStartedAt()),
        utc(j.getCompletedAt()),
        utc(j.getExpiresAt()),
        j.getCallbackAttempts(),
        j.getPhase() == null ? null : j.getPhase().name(),
        utc(j.getPhaseUpdatedAt()));
  }

  private static OffsetDateTime utc(Instant t) {
    return t == null ? null : t.atOffset(ZoneOffset.UTC);
  }
}

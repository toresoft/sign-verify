package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.model.ValidationPhase;

class JobSummaryDtoTest {

  private ValidationJob job(UUID id) {
    ValidationJob j = new ValidationJob();
    j.setId(id);
    j.setStatus(JobStatus.COMPLETED);
    j.setRequestedByPrincipalType(PrincipalType.OAUTH_USER);
    j.setRequestedByPrincipalId("user-123");
    j.setCreatedAt(Instant.parse("2026-07-14T22:13:24.272673Z"));
    j.setStartedAt(Instant.parse("2026-07-14T22:14:10.351944Z"));
    j.setCompletedAt(Instant.parse("2026-07-14T22:14:18.540216Z"));
    j.setExpiresAt(Instant.parse("2026-07-21T22:13:24.272673Z"));
    j.setCallbackAttempts(0);
    j.setPhase(ValidationPhase.RESULT_STORE);
    j.setPhaseUpdatedAt(Instant.parse("2026-07-14T22:14:18.530670Z"));
    return j;
  }

  @Test
  void from_mapsAllRequestedFields() {
    UUID id = UUID.randomUUID();
    JobSummaryDto dto = JobSummaryDto.from(job(id));

    assertThat(dto.jobId()).isEqualTo(id);
    assertThat(dto.status()).isEqualTo("COMPLETED");
    assertThat(dto.requestedBy().type()).isEqualTo("OAUTH_USER");
    assertThat(dto.requestedBy().id()).isEqualTo("user-123");
    assertThat(dto.createdAt())
        .isEqualTo(Instant.parse("2026-07-14T22:13:24.272673Z").atOffset(ZoneOffset.UTC));
    assertThat(dto.startedAt())
        .isEqualTo(Instant.parse("2026-07-14T22:14:10.351944Z").atOffset(ZoneOffset.UTC));
    assertThat(dto.completedAt())
        .isEqualTo(Instant.parse("2026-07-14T22:14:18.540216Z").atOffset(ZoneOffset.UTC));
    assertThat(dto.expiresAt())
        .isEqualTo(Instant.parse("2026-07-21T22:13:24.272673Z").atOffset(ZoneOffset.UTC));
    assertThat(dto.callbackAttempts()).isEqualTo(0);
    assertThat(dto.phase()).isEqualTo("RESULT_STORE");
    assertThat(dto.phaseUpdatedAt())
        .isEqualTo(Instant.parse("2026-07-14T22:14:18.530670Z").atOffset(ZoneOffset.UTC));
  }

  @Test
  void from_leavesNullableTimestampsAndPhaseNull() {
    ValidationJob j = job(UUID.randomUUID());
    j.setStartedAt(null);
    j.setCompletedAt(null);
    j.setPhase(null);
    j.setPhaseUpdatedAt(null);

    JobSummaryDto dto = JobSummaryDto.from(j);

    assertThat(dto.startedAt()).isNull();
    assertThat(dto.completedAt()).isNull();
    assertThat(dto.phase()).isNull();
    assertThat(dto.phaseUpdatedAt()).isNull();
  }
}

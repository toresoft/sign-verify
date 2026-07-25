package org.toresoft.signverify.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ValidationJob;

@DataJpaTest
@ActiveProfiles("test")
class JobSpecificationsTest {

  @Autowired private ValidationJobRepository repo;
  @Autowired private TestEntityManager em;

  private ValidationJob persist(
      PrincipalType type, String owner, JobStatus status, Instant createdAt) {
    ValidationJob j = new ValidationJob();
    j.setId(UUID.randomUUID());
    j.setStatus(status);
    j.setReportsRequested("simple");
    j.setRequestedByPrincipalType(type);
    j.setRequestedByPrincipalId(owner);
    j.setCreatedAt(createdAt);
    j.setExpiresAt(createdAt.plusSeconds(3600));
    j.setCallbackAttempts(0);
    return em.persist(j);
  }

  private static PageRequest firstPageByCreatedDesc() {
    return PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void ownedBy_matchesOnlyThatPrincipal() {
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T10:00:00Z"));
    persist(
        PrincipalType.API_KEY, "other", JobStatus.PENDING, Instant.parse("2026-07-14T10:00:00Z"));
    em.flush();

    var page =
        repo.findAll(
            JobSpecifications.ownedBy(PrincipalType.API_KEY, "me"), firstPageByCreatedDesc());

    assertThat(page.getContent())
        .extracting(ValidationJob::getRequestedByPrincipalId)
        .containsExactly("me");
  }

  @Test
  void statusIn_filtersToGivenStatuses() {
    persist(
        PrincipalType.API_KEY, "me", JobStatus.COMPLETED, Instant.parse("2026-07-14T10:00:00Z"));
    persist(PrincipalType.API_KEY, "me", JobStatus.FAILED, Instant.parse("2026-07-14T11:00:00Z"));
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T12:00:00Z"));
    em.flush();

    var spec = JobSpecifications.statusIn(List.of(JobStatus.COMPLETED, JobStatus.FAILED));
    var page = repo.findAll(spec, firstPageByCreatedDesc());

    assertThat(page.getContent())
        .extracting(ValidationJob::getStatus)
        .containsExactlyInAnyOrder(JobStatus.COMPLETED, JobStatus.FAILED);
  }

  @Test
  void createdAtRange_isFromInclusiveToExclusive() {
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-13T23:59:59Z"));
    persist(
        PrincipalType.API_KEY,
        "me",
        JobStatus.PENDING,
        Instant.parse("2026-07-14T00:00:00Z")); // from, inclusive
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T23:59:59Z"));
    persist(
        PrincipalType.API_KEY,
        "me",
        JobStatus.PENDING,
        Instant.parse("2026-07-15T00:00:00Z")); // to, exclusive
    em.flush();

    var spec =
        JobSpecifications.createdAtGte(Instant.parse("2026-07-14T00:00:00Z"))
            .and(JobSpecifications.createdAtLt(Instant.parse("2026-07-15T00:00:00Z")));
    var page = repo.findAll(spec, firstPageByCreatedDesc());

    assertThat(page.getContent())
        .extracting(ValidationJob::getCreatedAt)
        .containsExactly(
            Instant.parse("2026-07-14T23:59:59Z"),
            Instant.parse("2026-07-14T00:00:00Z")); // DESC order
  }

  @Test
  void nullArgs_areNoOps() {
    persist(PrincipalType.API_KEY, "me", JobStatus.PENDING, Instant.parse("2026-07-14T10:00:00Z"));
    em.flush();

    var spec =
        JobSpecifications.statusIn(null)
            .and(JobSpecifications.ownerIdEquals(null))
            .and(JobSpecifications.ownerTypeEquals(null))
            .and(JobSpecifications.createdAtGte(null))
            .and(JobSpecifications.createdAtLt(null));

    assertThat(repo.findAll(spec, firstPageByCreatedDesc()).getTotalElements()).isEqualTo(1);
  }
}

package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.persistence.ValidationJobRepository;
import org.toresoft.signverify.security.Principal;

@ExtendWith(MockitoExtension.class)
class JobQueryServiceTest {

  @Mock private ValidationJobRepository repo;
  @InjectMocks private JobQueryService service;

  @Captor
  private ArgumentCaptor<Specification<org.toresoft.signverify.domain.model.ValidationJob>>
      specCaptor;

  @Captor private ArgumentCaptor<PageRequest> pageCaptor;

  private final Principal standard =
      new Principal(PrincipalType.API_KEY, "me", Role.STANDARD, "me");
  private final Principal privileged =
      new Principal(PrincipalType.API_KEY, "admin", Role.PRIVILEGED, "admin");

  private JobQuery emptyQuery(int page, int size) {
    return new JobQuery(null, null, null, null, null, null, page, size);
  }

  private void stubEmptyPage() {
    when(repo.findAll(any(Specification.class), any(PageRequest.class)))
        .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
  }

  @Test
  void standard_cannotUseOwnerFilter() {
    JobQuery q = new JobQuery("someone-else", null, null, null, null, null, 0, 50);
    assertThatThrownBy(() -> service.list(standard, q))
        .isInstanceOf(AppException.class)
        .satisfies(
            e ->
                assertThat(((AppException) e).getDetail())
                    .contains("owner filter requires PRIVILEGED"));
  }

  @Test
  void createdOn_isMutuallyExclusiveWithRange() {
    JobQuery q =
        new JobQuery(
            null,
            null,
            null,
            LocalDate.parse("2026-07-14"),
            Instant.parse("2026-07-14T00:00:00Z"),
            null,
            0,
            50);
    assertThatThrownBy(() -> service.list(standard, q))
        .isInstanceOf(AppException.class)
        .satisfies(e -> assertThat(((AppException) e).getDetail()).contains("createdOn"));
  }

  @Test
  void fromAfterTo_isRejected() {
    JobQuery q =
        new JobQuery(
            null,
            null,
            null,
            null,
            Instant.parse("2026-07-15T00:00:00Z"),
            Instant.parse("2026-07-14T00:00:00Z"),
            0,
            50);
    assertThatThrownBy(() -> service.list(standard, q)).isInstanceOf(AppException.class);
  }

  @Test
  void size_isClampedTo200_andPageFloored() {
    stubEmptyPage();
    service.list(standard, emptyQuery(-3, 9999));
    verify(repo).findAll(specCaptor.capture(), pageCaptor.capture());
    assertThat(pageCaptor.getValue().getPageNumber()).isEqualTo(0);
    assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(200);
    assertThat(pageCaptor.getValue().getSort())
        .isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
  }

  @Test
  void size_nonPositiveFallsBackToDefault50() {
    stubEmptyPage();
    service.list(standard, emptyQuery(0, 0));
    verify(repo).findAll(any(Specification.class), pageCaptor.capture());
    assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  void privileged_withOwnerFilter_isAllowed() {
    stubEmptyPage();
    JobQuery q =
        new JobQuery(
            "some-owner",
            PrincipalType.OAUTH_USER,
            List.of(JobStatus.COMPLETED),
            null,
            null,
            null,
            0,
            50);
    JobPage result = service.list(privileged, q);
    assertThat(result.page()).isEqualTo(0);
    assertThat(result.content()).isEmpty();
    verify(repo).findAll(any(Specification.class), any(PageRequest.class));
  }
}

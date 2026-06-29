package org.toresoft.signverify.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.toresoft.signverify.persistence.ValidationJobRepository;

class JobQueueHealthIndicatorTest {

  @Test
  void reports_up_with_active_job_count() {
    var repo = mock(ValidationJobRepository.class);
    when(repo.countActiveGlobal()).thenReturn(7L);

    var health = new JobQueueHealthIndicator(repo).health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).containsEntry("activeJobs", 7L);
  }
}

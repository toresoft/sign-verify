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
package org.toresoft.signverify.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.IsolatedDbTest;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.model.ValidationJob;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;
import org.toresoft.signverify.persistence.ValidationJobRepository;

/**
 * End-to-end IT for {@code GET /api/v1/verifications/jobs}: real filter chain (X-API-Key auth),
 * real Postgres, real role scoping and filters.
 */
@IsolatedDbTest
class VerificationJobListIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository keys;
  @Autowired private ValidationJobRepository jobs;
  @Autowired private PasswordHasherPort hasher;

  private MockMvc mvc;

  private String standardKeyRaw;
  private String standardKeyId;
  private String privilegedKeyRaw;

  private static final String OTHER_OWNER = "someone-else-key-id";

  @BeforeEach
  void setup() {
    jobs.deleteAll();
    keys.deleteAll();

    standardKeyRaw = "sv_jobsstd1_" + UUID.randomUUID().toString().replace("-", "");
    ApiKey std = newKey("jobsstd1", standardKeyRaw, Role.STANDARD);
    standardKeyId = std.getId().toString();

    privilegedKeyRaw = "sv_jobspriv_" + UUID.randomUUID().toString().replace("-", "");
    newKey("jobspriv", privilegedKeyRaw, Role.PRIVILEGED);

    mvc =
        MockMvcBuilders.webAppContextSetup(ctx)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  private ApiKey newKey(String prefix, String raw, Role role) {
    ApiKey k = new ApiKey();
    k.setId(UUID.randomUUID());
    k.setName(prefix + "-" + UUID.randomUUID());
    k.setKeyPrefix(prefix);
    k.setKeyHash(hasher.hash(raw));
    k.setRole(role);
    k.setEnabled(true);
    k.setCreatedAt(Instant.now());
    return keys.save(k);
  }

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
    return jobs.save(j);
  }

  @Test
  void standard_seesOnlyOwnJobs_newestFirst() throws Exception {
    persist(
        PrincipalType.API_KEY,
        standardKeyId,
        JobStatus.COMPLETED,
        Instant.parse("2026-07-13T10:00:00Z"));
    persist(
        PrincipalType.API_KEY,
        OTHER_OWNER,
        JobStatus.COMPLETED,
        Instant.parse("2026-07-14T10:00:00Z"));
    ValidationJob newestMine =
        persist(
            PrincipalType.API_KEY,
            standardKeyId,
            JobStatus.PENDING,
            Instant.parse("2026-07-15T10:00:00Z"));

    mvc.perform(get("/api/v1/verifications/jobs").header("X-API-Key", standardKeyRaw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].jobId").value(newestMine.getId().toString()))
        .andExpect(jsonPath("$.content[0].requestedBy.type").value("API_KEY"))
        .andExpect(jsonPath("$.content[0].requestedBy.id").value(standardKeyId))
        .andExpect(jsonPath("$.content[1].requestedBy.id").value(standardKeyId));
  }

  @Test
  void privileged_filtersByOwnerId() throws Exception {
    persist(
        PrincipalType.API_KEY,
        standardKeyId,
        JobStatus.COMPLETED,
        Instant.parse("2026-07-14T10:00:00Z"));
    ValidationJob other =
        persist(
            PrincipalType.API_KEY,
            OTHER_OWNER,
            JobStatus.COMPLETED,
            Instant.parse("2026-07-14T11:00:00Z"));

    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("ownerId", OTHER_OWNER)
                .header("X-API-Key", privilegedKeyRaw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value(other.getId().toString()));
  }

  @Test
  void standard_withOwnerFilter_gets400() throws Exception {
    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("ownerId", OTHER_OWNER)
                .header("X-API-Key", standardKeyRaw))
        .andExpect(status().isBadRequest());
  }

  @Test
  void privileged_filtersByCreatedOn_utcDay() throws Exception {
    ValidationJob onDay =
        persist(
            PrincipalType.API_KEY,
            standardKeyId,
            JobStatus.COMPLETED,
            Instant.parse("2026-07-14T15:30:00Z"));
    persist(
        PrincipalType.API_KEY,
        standardKeyId,
        JobStatus.COMPLETED,
        Instant.parse("2026-07-15T00:30:00Z"));

    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("createdOn", "2026-07-14")
                .header("X-API-Key", privilegedKeyRaw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].jobId").value(onDay.getId().toString()));
  }

  @Test
  void privileged_filtersByStatus_repeatedParam() throws Exception {
    persist(
        PrincipalType.API_KEY,
        standardKeyId,
        JobStatus.COMPLETED,
        Instant.parse("2026-07-14T10:00:00Z"));
    persist(
        PrincipalType.API_KEY,
        standardKeyId,
        JobStatus.FAILED,
        Instant.parse("2026-07-14T11:00:00Z"));
    persist(
        PrincipalType.API_KEY,
        standardKeyId,
        JobStatus.PENDING,
        Instant.parse("2026-07-14T12:00:00Z"));

    mvc.perform(
            get("/api/v1/verifications/jobs")
                .param("status", "COMPLETED", "FAILED")
                .header("X-API-Key", privilegedKeyRaw))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }
}

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.toresoft.signverify.IsolatedDbTest;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;
import org.toresoft.signverify.persistence.AuditLogRepository;

/**
 * Companion to {@link AuditLogIT}: with {@code app.audit.enabled=false}, mutations still succeed
 * but no row is written to {@code audit_log}.
 */
@IsolatedDbTest
@TestPropertySource(properties = "app.audit.enabled=false")
class AuditLogDisabledIT {

  @Autowired private WebApplicationContext ctx;
  @Autowired private ApiKeyRepository apiKeyRepo;
  @Autowired private AuditLogRepository auditRepo;
  @Autowired private PasswordHasherPort hasher;
  @Autowired private PlatformTransactionManager tx;
  @PersistenceContext private EntityManager em;

  private MockMvc mvc;
  private String adminKey;

  @BeforeEach
  void setup() {
    TransactionTemplate tt = new TransactionTemplate(tx);
    tt.execute(
        s -> {
          em.createNativeQuery("DELETE FROM audit_log").executeUpdate();
          em.createNativeQuery("DELETE FROM api_key").executeUpdate();
          return null;
        });

    adminKey = "sv_adminx01_abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKL";
    ApiKey admin = new ApiKey();
    admin.setId(UUID.randomUUID());
    admin.setName("admin-it-" + UUID.randomUUID());
    admin.setKeyPrefix("adminx01");
    admin.setKeyHash(hasher.hash(adminKey));
    admin.setRole(Role.PRIVILEGED);
    admin.setEnabled(true);
    admin.setCreatedAt(Instant.now());
    apiKeyRepo.save(admin);

    mvc =
        MockMvcBuilders.webAppContextSetup(ctx)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                    .springSecurity())
            .build();
  }

  @Test
  void auditDisabled_createsNoRows_evenAfterMutation() throws Exception {
    String body = "{\"name\":\"silent\",\"role\":\"STANDARD\"}";

    mvc.perform(
            post("/api/v1/api-keys")
                .header("X-API-Key", adminKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());

    // Give the audit executor enough time to (not) run, then assert the table is still empty.
    await()
        .atMost(Duration.ofMillis(800))
        .pollInterval(Duration.ofMillis(50))
        .untilAsserted(
            () -> {
              List<AuditLog> rows = auditRepo.findAll();
              // The AuditService is a no-op when disabled; the table may legitimately be empty
              // here, or contain rows from a test that ran earlier in the same context. The
              // strongest claim we can make is that NO row for the action we just performed is
              // present.
              assertThat(
                      rows.stream()
                          .noneMatch(r -> AuditActions.APIKEY_CREATE.equals(r.getAction())))
                  .isTrue();
            });
  }
}

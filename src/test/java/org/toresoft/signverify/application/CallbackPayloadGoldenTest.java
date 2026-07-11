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

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.model.JobStatus;
import org.toresoft.signverify.domain.model.ValidationJob;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden test that pins the exact JSON byte form produced by {@link CallbackWorker#buildBody} for a
 * failed job. The callback payload is HMAC-signed by external receivers — any drift in JSON form
 * (field order, whitespace, encoding) causes the receiver to reject the callback. This test pins
 * the exact output.
 */
@SpringBootTest
@ActiveProfiles("test")
class CallbackPayloadGoldenTest {

  @Autowired private JsonMapper om;

  @Test
  void failedJob_buildBody_producesExactJsonBytes() throws Exception {
    CallbackWorker worker =
        new CallbackWorker(
            null,
            null,
            null,
            null,
            om,
            3,
            List.of(Duration.ofSeconds(1)),
            List.of(200),
            List.of(400),
            Runnable::run,
            4,
            Duration.ofMinutes(5));

    ValidationJob job = new ValidationJob();
    job.setId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    job.setStatus(JobStatus.FAILED);
    job.setErrorCode("job.expired");
    job.setErrorMessage("job expired before completion");

    Method buildBody = CallbackWorker.class.getDeclaredMethod("buildBody", ValidationJob.class);
    buildBody.setAccessible(true);
    byte[] bytes = (byte[]) buildBody.invoke(worker, job);

    String json = new String(bytes, StandardCharsets.UTF_8);
    assertThat(json)
        .isEqualTo(
            "{\"jobId\":\"00000000-0000-0000-0000-000000000001\",\"status\":\"FAILED\",\"errorCode\":\"job.expired\",\"error\":\"job expired before completion\"}");
  }
}

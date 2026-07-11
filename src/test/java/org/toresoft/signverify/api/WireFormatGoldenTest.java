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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.port.CertificateSummary;
import tools.jackson.databind.json.JsonMapper;

/**
 * Golden test that pins the exact JSON wire format for DTOs containing {@link OffsetDateTime}
 * fields. Any change in Jackson's date serialization (e.g. Jackson 3 migration) will cause this
 * test to fail, forcing an explicit decision on the wire format.
 */
@SpringBootTest
@ActiveProfiles("test")
class WireFormatGoldenTest {

  @Autowired private JsonMapper om;

  @Test
  void certificateSummary_serialization_is_stable() throws Exception {
    var dto =
        new CertificateSummary("cert-1", "CN=Test", OffsetDateTime.parse("2026-01-15T10:30:00Z"));

    String json = om.writeValueAsString(dto);

    assertThat(json)
        .isEqualTo(
            "{\"id\":\"cert-1\",\"qualifiedName\":\"CN=Test\",\"sunsetDate\":\"2026-01-15T10:30:00Z\"}");
  }
}

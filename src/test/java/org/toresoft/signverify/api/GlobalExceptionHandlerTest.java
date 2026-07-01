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

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.toresoft.signverify.domain.exception.AppException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler h = new GlobalExceptionHandler();

  @Test
  void app_exception_maps_to_problem() {
    MockHttpServletRequest httpReq = new MockHttpServletRequest("GET", "/api/v1/test");
    WebRequest req = new ServletWebRequest(httpReq);
    ResponseEntity<?> res = h.handleApp(AppException.notFound("nope"), req);
    assertThat(res.getStatusCode().value()).isEqualTo(404);
    assertThat(res.getHeaders().getContentType().toString()).contains("application/problem+json");
    var body = (java.util.Map<?, ?>) res.getBody();
    assertThat(body.get("type")).isEqualTo("urn:signverify:error:resource.not-found");
    assertThat(body.get("status")).isEqualTo(404);
    assertThat(body.get("detail")).isEqualTo("nope");
  }

  @Test
  void conflict_maps_to_409() {
    MockHttpServletRequest httpReq = new MockHttpServletRequest("DELETE", "/api/v1/api-keys/123");
    WebRequest req = new ServletWebRequest(httpReq);
    ResponseEntity<?> res = h.handleApp(AppException.conflict("last privileged"), req);
    assertThat(res.getStatusCode().value()).isEqualTo(409);
    var body = (java.util.Map<?, ?>) res.getBody();
    assertThat(body.get("type")).isEqualTo("urn:signverify:error:resource.conflict");
  }
}

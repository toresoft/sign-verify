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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.security.Principal;

class RequestContextFilterTest {

  private final RequestContextFilter filter = new RequestContextFilter();

  @BeforeEach
  void clearMdc() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void cleanupMdc() {
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_setsRequestIdInMdc() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    AtomicReference<String> capturedRequestId = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest r, ServletResponse s) {
            capturedRequestId.set(MDC.get("requestId"));
          }
        };

    filter.doFilter(req, res, chain);

    assertThat(capturedRequestId.get()).isNotNull();
    assertThat(UUID.fromString(capturedRequestId.get())).isNotNull();
  }

  @Test
  void doFilter_setsClientIpFromHttpRequest() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.setRemoteAddr("1.2.3.4");
    MockHttpServletResponse res = new MockHttpServletResponse();
    AtomicReference<String> capturedClientIp = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest r, ServletResponse s) {
            capturedClientIp.set(MDC.get("clientIp"));
          }
        };

    filter.doFilter(req, res, chain);

    assertThat(capturedClientIp.get()).isEqualTo("1.2.3.4");
  }

  @Test
  void doFilter_setsPrincipalTypeAndId_whenAuthenticated() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    Principal p = new Principal(PrincipalType.OAUTH_USER, "user-42", Role.STANDARD, "Alice");
    var auth = new UsernamePasswordAuthenticationToken(p, "n/a", java.util.List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);

    AtomicReference<String> capturedType = new AtomicReference<>();
    AtomicReference<String> capturedId = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest r, ServletResponse s) {
            capturedType.set(MDC.get("principalType"));
            capturedId.set(MDC.get("principalId"));
          }
        };

    filter.doFilter(req, res, chain);

    assertThat(capturedType.get()).isEqualTo("OAUTH_USER");
    assertThat(capturedId.get()).isEqualTo("user-42");
  }

  @Test
  void doFilter_noPrincipal_noMdcEntries() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    SecurityContextHolder.clearContext();

    AtomicReference<String> capturedType = new AtomicReference<>();
    AtomicReference<String> capturedId = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest r, ServletResponse s) {
            capturedType.set(MDC.get("principalType"));
            capturedId.set(MDC.get("principalId"));
          }
        };

    filter.doFilter(req, res, chain);

    assertThat(capturedType.get()).isNull();
    assertThat(capturedId.get()).isNull();
  }

  @Test
  void doFilter_clearsMdcAfterChain() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(req, res, chain);

    assertThat(MDC.get("requestId")).isNull();
    assertThat(MDC.get("clientIp")).isNull();
    assertThat(MDC.get("principalType")).isNull();
    assertThat(MDC.get("principalId")).isNull();
  }

  @Test
  void doFilter_clearsMdcEvenOnException() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest();
    MockHttpServletResponse res = new MockHttpServletResponse();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest r, ServletResponse s)
              throws ServletException, java.io.IOException {
            throw new ServletException("boom");
          }
        };

    assertThatThrownBy(() -> filter.doFilter(req, res, chain)).isInstanceOf(ServletException.class);

    assertThat(MDC.get("requestId")).isNull();
    assertThat(MDC.get("clientIp")).isNull();
  }

  @Test
  void doFilter_requestIdIsUniquePerRequest() throws Exception {
    MockHttpServletRequest req1 = new MockHttpServletRequest();
    MockHttpServletResponse res1 = new MockHttpServletResponse();
    AtomicReference<String> id1 = new AtomicReference<>();
    MockFilterChain chain1 =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest r, ServletResponse s) {
            id1.set(MDC.get("requestId"));
          }
        };

    filter.doFilter(req1, res1, chain1);

    MockHttpServletRequest req2 = new MockHttpServletRequest();
    MockHttpServletResponse res2 = new MockHttpServletResponse();
    AtomicReference<String> id2 = new AtomicReference<>();
    MockFilterChain chain2 =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest r, ServletResponse s) {
            id2.set(MDC.get("requestId"));
          }
        };

    filter.doFilter(req2, res2, chain2);

    assertThat(id1.get()).isNotNull();
    assertThat(id2.get()).isNotNull();
    assertThat(id1.get()).isNotEqualTo(id2.get());
  }

  @Test
  void doFilter_passesRequestAndResponseToChain() throws Exception {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/test");
    MockHttpServletResponse res = new MockHttpServletResponse();
    AtomicReference<ServletRequest> seenReq = new AtomicReference<>();
    AtomicReference<ServletResponse> seenRes = new AtomicReference<>();
    MockFilterChain chain =
        new MockFilterChain() {
          @Override
          public void doFilter(ServletRequest r, ServletResponse s) {
            seenReq.set(r);
            seenRes.set(s);
          }
        };

    filter.doFilter(req, res, chain);

    assertThat(seenReq.get()).isSameAs(req);
    assertThat(seenRes.get()).isSameAs(res);
  }
}

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
package org.toresoft.signverify.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.toresoft.signverify.security.ApiKeyAuthenticationFilter;
import org.toresoft.signverify.security.OAuthPrincipalConverter;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http,
      ApiKeyAuthenticationFilter apiKeyFilter,
      @Value("${app.security.oauth.enabled}") boolean oauthEnabled,
      @Value("${app.security.oauth.role-claim}") String roleClaim,
      @Value("${app.security.oauth.privileged-values}") List<String> privilegedValues)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        // Write security headers BEFORE the controller runs instead of on filter-chain unwind.
        // The default (lazy) mode mutates response headers after chain.doFilter(): on streaming
        // endpoints (StreamingResponseBody, e.g. /api/v1/extractions) the MVC executor thread is
        // already writing/committing the response at that point, and the two threads corrupt
        // Tomcat's non-thread-safe MimeHeaders (NPE "headers[i] is null"), killing the connection.
        .headers(
            h ->
                h.addObjectPostProcessor(
                    new ObjectPostProcessor<HeaderWriterFilter>() {
                      @Override
                      public <O extends HeaderWriterFilter> O postProcess(O filter) {
                        filter.setShouldWriteHeadersEagerly(true);
                        return filter;
                      }
                    }))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**")
                    .permitAll()
                    // /actuator/prometheus is NOT public: it leaks per-route latency/traffic
                    // (API fingerprint), async queue depth (times a DoS against max-pending-global)
                    // and JVM/version details. It falls through to authenticated() below — the
                    // scraper must present an API key / bearer token (or be network-isolated).
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

    if (oauthEnabled) {
      OAuthPrincipalConverter conv = new OAuthPrincipalConverter(roleClaim, privilegedValues);
      http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(conv)));
    }
    return http.build();
  }
}

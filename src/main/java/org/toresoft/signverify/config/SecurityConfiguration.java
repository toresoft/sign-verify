package org.toresoft.signverify.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
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
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health/**", "/actuator/prometheus")
                    .permitAll()
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

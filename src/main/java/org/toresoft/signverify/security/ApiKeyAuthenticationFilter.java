package org.toresoft.signverify.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.toresoft.signverify.domain.model.ApiKey;
import org.toresoft.signverify.domain.port.PasswordHasherPort;
import org.toresoft.signverify.persistence.ApiKeyRepository;

@org.springframework.stereotype.Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  public static final String HEADER = "X-API-Key";
  public static final int PREFIX_LENGTH = 8;

  private final ApiKeyRepository repo;
  private final PasswordHasherPort hasher;

  public ApiKeyAuthenticationFilter(ApiKeyRepository repo, PasswordHasherPort hasher) {
    this.repo = repo;
    this.hasher = hasher;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {

    String raw = req.getHeader(HEADER);
    if (raw == null || raw.isBlank()) {
      chain.doFilter(req, res);
      return;
    }

    String prefix = extractPrefix(raw);
    if (prefix == null) {
      writeUnauthorized(res, "auth.invalid-token");
      return;
    }

    Optional<ApiKey> opt = repo.findByKeyPrefix(prefix);
    if (opt.isEmpty()
        || !opt.get().isEnabled()
        || isExpired(opt.get())
        || !hasher.matches(raw, opt.get().getKeyHash())) {
      writeUnauthorized(res, "auth.invalid-token");
      return;
    }

    ApiKey key = opt.get();
    Principal p =
        new Principal(
            org.toresoft.signverify.domain.model.PrincipalType.API_KEY,
            key.getId().toString(),
            key.getRole(),
            key.getName());
    SecurityContextHolder.getContext().setAuthentication(new ApiKeyAuthentication(p));
    chain.doFilter(req, res);
  }

  private String extractPrefix(String raw) {
    if (!raw.startsWith("sv_") || raw.length() < 3 + PREFIX_LENGTH + 1) return null;
    return raw.substring(3, 3 + PREFIX_LENGTH);
  }

  private boolean isExpired(ApiKey k) {
    return k.getExpiresAt() != null && k.getExpiresAt().isBefore(Instant.now());
  }

  private void writeUnauthorized(HttpServletResponse res, String code) throws IOException {
    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    res.setContentType("application/problem+json");
    res.getWriter()
        .write(
            """
        {"type":"urn:signverify:error:%s","title":"Unauthorized","status":401}
        """
                .formatted(code));
  }
}

package org.toresoft.signverify.api;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.security.Principal;

@Component("mdcRequestFilter")
public class RequestContextFilter implements Filter {

  @Override
  public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
      throws IOException, ServletException {
    String requestId = UUID.randomUUID().toString();
    MDC.put("requestId", requestId);
    if (req instanceof HttpServletRequest http) {
      MDC.put("clientIp", http.getRemoteAddr());
    }
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.getPrincipal() instanceof Principal p) {
      MDC.put("principalType", p.type().name());
      MDC.put("principalId", p.id());
    }
    try {
      chain.doFilter(req, res);
    } finally {
      MDC.clear();
    }
  }
}

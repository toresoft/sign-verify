package org.toresoft.signverify.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.toresoft.signverify.security.Principal;

@RestController
public class HealthProbeController {

  @GetMapping("/internal/whoami")
  public Principal whoami(@AuthenticationPrincipal Principal p) {
    return p;
  }

  @GetMapping("/internal/admin")
  @PreAuthorize("hasRole('PRIVILEGED')")
  public String admin() {
    return "ok";
  }
}

package org.toresoft.signverify.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the audit log subsystem.
 *
 * <p>Bound to the {@code app.audit} prefix in {@code application.yaml}. When {@link #enabled} is
 * false the {@code AuditService} becomes a no-op so the audit table can be turned off in dev /
 * performance tests without code changes.
 */
@ConfigurationProperties(prefix = "app.audit")
public class AuditProperties {

  /** Master switch for audit writes. Default {@code true}. */
  private boolean enabled = true;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}

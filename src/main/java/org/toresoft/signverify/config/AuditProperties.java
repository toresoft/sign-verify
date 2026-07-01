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

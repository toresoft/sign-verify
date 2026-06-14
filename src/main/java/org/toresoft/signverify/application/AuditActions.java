package org.toresoft.signverify.application;

/**
 * Canonical action names written to the {@code audit_log.action} column.
 *
 * <p>Values are short kebab-case strings constrained to the column length (60 chars). The companion
 * {@code target_type} and {@code target_id} columns are documented in the {@code Cablaggio Audit
 * Log} plan.
 */
public final class AuditActions {

  private AuditActions() {}

  public static final String APIKEY_CREATE = "apikey.create";
  public static final String APIKEY_DELETE = "apikey.delete";
  public static final String APIKEY_UPDATE = "apikey.update";
  public static final String APIKEY_LAST_PRIVILEGED_BLOCKED = "apikey.last_privileged_blocked";

  public static final String PROFILE_CREATE = "profile.create";
  public static final String PROFILE_UPDATE = "profile.update";
  public static final String PROFILE_DELETE = "profile.delete";
  public static final String PROFILE_SET_DEFAULT = "profile.set_default";

  public static final String JOB_SUBMIT = "job.submit";
  public static final String JOB_CLEANUP = "job.cleanup";

  public static final String TSL_REFRESH = "tsl.refresh";

  public static final String AUTH_DENIED = "auth.denied";
}

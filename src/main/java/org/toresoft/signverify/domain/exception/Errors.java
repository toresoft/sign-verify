package org.toresoft.signverify.domain.exception;

public final class Errors {
  public static final String VALIDATION_INVALID_INPUT = "validation.invalid-input";
  public static final String VALIDATION_INVALID_OVERRIDES = "validation.invalid-profile-overrides";
  public static final String AUTH_MISSING = "auth.missing-credentials";
  public static final String AUTH_INVALID = "auth.invalid-token";
  public static final String AUTHZ_FORBIDDEN = "authz.forbidden";
  public static final String RESOURCE_NOT_FOUND = "resource.not-found";
  public static final String RESOURCE_GONE = "resource.gone";
  public static final String RESOURCE_CONFLICT = "resource.conflict";
  public static final String PAYLOAD_TOO_LARGE = "payload.too-large";
  public static final String MEDIA_UNSUPPORTED = "media.unsupported";
  public static final String SIGNATURE_PARSE_ERROR = "signature.parse-error";
  public static final String EXCESSIVE_LOAD_CONCURRENCY = "excessive-load.concurrency";
  public static final String EXCESSIVE_LOAD_ASYNC = "excessive-load.async-backpressure";
  public static final String TSL_NOT_READY = "tsl.not-ready";
  public static final String DSS_UNAVAILABLE = "dss.unavailable";
  public static final String INTERNAL = "internal-error";
  private Errors() {}
}

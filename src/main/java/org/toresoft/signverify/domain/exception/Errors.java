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

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
package org.toresoft.signverify.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.exception.Errors;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(AppException.class)
  public ResponseEntity<Map<String, Object>> handleApp(AppException ex, WebRequest req) {
    return problem(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetail(), req);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidation(
      MethodArgumentNotValidException ex, WebRequest req) {
    String detail =
        ex.getBindingResult().getAllErrors().stream()
            .map(e -> e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("invalid input");
    return problem(400, Errors.VALIDATION_INVALID_INPUT, "Bad Request", detail, req);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleNotReadable(
      HttpMessageNotReadableException ex, WebRequest req) {
    return problem(
        400, Errors.VALIDATION_INVALID_INPUT, "Bad Request", "malformed request body", req);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleTooLarge(
      MaxUploadSizeExceededException ex, WebRequest req) {
    return problem(
        413, Errors.PAYLOAD_TOO_LARGE, "Payload Too Large", "max upload size exceeded", req);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> handleAccessDenied(
      AccessDeniedException ex, WebRequest req) {
    return problem(403, Errors.AUTHZ_FORBIDDEN, "Forbidden", "insufficient role", req);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<Map<String, Object>> handleAuth(
      AuthenticationException ex, WebRequest req) {
    return problem(401, Errors.AUTH_INVALID, "Unauthorized", "invalid credentials", req);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, WebRequest req) {
    log.error("Uncaught exception", ex);
    return problem(500, Errors.INTERNAL, "Internal Server Error", "unexpected error", req);
  }

  private ResponseEntity<Map<String, Object>> problem(
      int status, String code, String title, String detail, WebRequest req) {
    var body = new java.util.LinkedHashMap<String, Object>();
    body.put("type", URI.create("urn:signverify:error:" + code).toString());
    body.put("title", title);
    body.put("status", status);
    if (detail != null) body.put("detail", detail);
    if (req instanceof org.springframework.web.context.request.ServletWebRequest swr) {
      HttpServletRequest http = swr.getRequest();
      body.put("instance", http.getRequestURI());
    }
    return ResponseEntity.status(status)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .body(body);
  }
}

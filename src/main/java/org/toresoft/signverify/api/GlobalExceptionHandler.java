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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.exception.Errors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

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
    List<Map<String, Object>> invalidParams = new ArrayList<>();
    for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
      invalidParams.add(invalidParam(fe.getField(), fe.getDefaultMessage()));
    }
    for (ObjectError oe : ex.getBindingResult().getGlobalErrors()) {
      invalidParams.add(invalidParam(oe.getObjectName(), oe.getDefaultMessage()));
    }
    String detail = invalidParams.isEmpty() ? "invalid input" : summarize(invalidParams);
    return problem(400, Errors.VALIDATION_INVALID_INPUT, "Bad Request", detail, req, invalidParams);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<Map<String, Object>> handleNotReadable(
      HttpMessageNotReadableException ex, WebRequest req) {
    // When Jackson can locate the offending property (wrong type, bad enum, etc.) surface it as an
    // invalid-param so the client learns which field is wrong; a pure syntax error has no path.
    List<Map<String, Object>> invalidParams = new ArrayList<>();
    if (ex.getCause() instanceof JacksonException je
        && je.getPath() != null
        && !je.getPath().isEmpty()) {
      invalidParams.add(invalidParam(jsonPath(je), reasonFor(je)));
    }
    String detail = invalidParams.isEmpty() ? "malformed request body" : summarize(invalidParams);
    return problem(400, Errors.VALIDATION_INVALID_INPUT, "Bad Request", detail, req, invalidParams);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, WebRequest req) {
    // A path/query value that cannot be coerced to the declared type (bad UUID, non-numeric page,
    // ...). Without this it falls through to the generic 500; it is a client input error → 400.
    Class<?> required = ex.getRequiredType();
    String reason =
        required != null ? "must be a valid " + required.getSimpleName() : "invalid value";
    List<Map<String, Object>> invalidParams = List.of(invalidParam(ex.getName(), reason));
    return problem(
        400,
        Errors.VALIDATION_INVALID_INPUT,
        "Bad Request",
        summarize(invalidParams),
        req,
        invalidParams);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> handleMissingParam(
      MissingServletRequestParameterException ex, WebRequest req) {
    List<Map<String, Object>> invalidParams =
        List.of(invalidParam(ex.getParameterName(), "required parameter is missing"));
    return problem(
        400,
        Errors.VALIDATION_INVALID_INPUT,
        "Bad Request",
        summarize(invalidParams),
        req,
        invalidParams);
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<Map<String, Object>> handleHandlerMethodValidation(
      HandlerMethodValidationException ex, WebRequest req) {
    // Bean-validation constraints on individual @RequestParam/@PathVariable/@RequestPart arguments.
    List<Map<String, Object>> invalidParams = new ArrayList<>();
    for (ParameterValidationResult result : ex.getParameterValidationResults()) {
      String paramName = result.getMethodParameter().getParameterName();
      for (MessageSourceResolvable error : result.getResolvableErrors()) {
        String name = error instanceof FieldError fe ? fe.getField() : paramName;
        invalidParams.add(
            invalidParam(name != null ? name : "parameter", error.getDefaultMessage()));
      }
    }
    String detail = invalidParams.isEmpty() ? "invalid input" : summarize(invalidParams);
    return problem(400, Errors.VALIDATION_INVALID_INPUT, "Bad Request", detail, req, invalidParams);
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
    return problem(status, code, title, detail, req, null);
  }

  private ResponseEntity<Map<String, Object>> problem(
      int status,
      String code,
      String title,
      String detail,
      WebRequest req,
      List<Map<String, Object>> invalidParams) {
    var body = new LinkedHashMap<String, Object>();
    body.put("type", URI.create("urn:signverify:error:" + code).toString());
    // RFC 9457 extension member: the bare catalog code (see Errors), so clients need not parse
    // the type URN.
    body.put("code", code);
    body.put("title", title);
    body.put("status", status);
    if (detail != null) body.put("detail", detail);
    if (req instanceof org.springframework.web.context.request.ServletWebRequest swr) {
      HttpServletRequest http = swr.getRequest();
      body.put("instance", http.getRequestURI());
    }
    // RFC 9457 extension member (spec example, section 3.1): one entry per rejected field so
    // clients can bind the failure to a specific input rather than parsing a flat message.
    if (invalidParams != null && !invalidParams.isEmpty()) {
      body.put("invalid-params", invalidParams);
    }
    return ResponseEntity.status(status)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .body(body);
  }

  private static Map<String, Object> invalidParam(String name, String reason) {
    var m = new LinkedHashMap<String, Object>();
    m.put("name", name);
    m.put("reason", reason != null ? reason : "invalid value");
    return m;
  }

  private static String summarize(List<Map<String, Object>> invalidParams) {
    return invalidParams.stream()
        .map(p -> p.get("name") + ": " + p.get("reason"))
        .reduce((a, b) -> a + "; " + b)
        .orElse("invalid input");
  }

  /** Dotted/indexed JSON path to the property Jackson rejected, e.g. {@code items[2].role}. */
  private static String jsonPath(JacksonException je) {
    var sb = new StringBuilder();
    for (JacksonException.Reference ref : je.getPath()) {
      if (ref.getPropertyName() != null) {
        if (sb.length() > 0) sb.append('.');
        sb.append(ref.getPropertyName());
      } else if (ref.getIndex() >= 0) {
        sb.append('[').append(ref.getIndex()).append(']');
      }
    }
    return sb.length() == 0 ? "body" : sb.toString();
  }

  private static String reasonFor(JacksonException je) {
    if (je instanceof InvalidFormatException || je instanceof MismatchedInputException) {
      return "invalid value";
    }
    return "invalid or missing value";
  }
}

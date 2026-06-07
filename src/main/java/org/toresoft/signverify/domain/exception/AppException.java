package org.toresoft.signverify.domain.exception;

public class AppException extends RuntimeException {
  private final String code;
  private final int status;
  private final String detail;

  public AppException(String code, int status, String title, String detail) {
    super(title);
    this.code = code;
    this.status = status;
    this.detail = detail;
  }
  public String getCode() { return code; }
  public int getStatus() { return status; }
  public String getDetail() { return detail; }

  public static AppException notFound(String detail) {
    return new AppException(Errors.RESOURCE_NOT_FOUND, 404, "Not Found", detail);
  }
  public static AppException conflict(String detail) {
    return new AppException(Errors.RESOURCE_CONFLICT, 409, "Conflict", detail);
  }
  public static AppException badRequest(String detail) {
    return new AppException(Errors.VALIDATION_INVALID_INPUT, 400, "Bad Request", detail);
  }
  public static AppException gone(String detail) {
    return new AppException(Errors.RESOURCE_GONE, 410, "Gone", detail);
  }
  public static AppException tooLarge(String detail) {
    return new AppException(Errors.PAYLOAD_TOO_LARGE, 413, "Payload Too Large", detail);
  }
  public static AppException backpressure(String detail) {
    return new AppException(Errors.EXCESSIVE_LOAD_ASYNC, 429, "Too Many Requests", detail);
  }
  public static AppException concurrency(String detail) {
    return new AppException(Errors.EXCESSIVE_LOAD_CONCURRENCY, 503, "Service Unavailable", detail);
  }
  public static AppException dssUnavailable(String detail) {
    return new AppException(Errors.DSS_UNAVAILABLE, 503, "DSS Unavailable", detail);
  }
  public static AppException tslNotReady(String detail) {
    return new AppException(Errors.TSL_NOT_READY, 503, "TSL Not Ready", detail);
  }
  public static AppException signatureParseError(String detail) {
    return new AppException(Errors.SIGNATURE_PARSE_ERROR, 422, "Unprocessable Entity", detail);
  }
}

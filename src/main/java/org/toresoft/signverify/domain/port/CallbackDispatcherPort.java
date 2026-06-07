package org.toresoft.signverify.domain.port;

import java.util.Set;

public interface CallbackDispatcherPort {
  DispatchResult dispatch(
      String url,
      String algorithm,
      String secret,
      byte[] body,
      String jobId,
      String deliveryId,
      int attempt);

  record DispatchResult(int statusCode, String errorMessage) {
    public boolean success(Set<Integer> successCodes) {
      return errorMessage == null && successCodes.contains(statusCode);
    }

    public boolean nonRetryable(Set<Integer> nonRetryableCodes) {
      return errorMessage == null && nonRetryableCodes.contains(statusCode);
    }
  }
}

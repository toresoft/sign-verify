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

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.toresoft.signverify.application.TslService;
import org.toresoft.signverify.domain.exception.AppException;

@ExtendWith(MockitoExtension.class)
class TslControllerTest {

  @Mock private TslService tslService;
  @Mock private org.toresoft.signverify.application.AuditService audit;

  private static final int MAX_SIZE = 100;

  private Object listWith(int page, int size) {
    TslController controller = new TslController(tslService, audit);
    return controller.list(
        null, null, null, null, null, null, null, null, null, null, null, null, false, page, size);
  }

  @Test
  void list_negativePage_isRejected() {
    assertThatThrownBy(() -> listWith(-1, 50))
        .isInstanceOf(AppException.class)
        .satisfies(e -> assertThat(((AppException) e).getDetail()).contains("page"));
  }

  @Test
  void list_sizeAboveMax_isRejected() {
    assertThatThrownBy(() -> listWith(0, MAX_SIZE + 1))
        .isInstanceOf(AppException.class)
        .satisfies(e -> assertThat(((AppException) e).getDetail()).contains("size"));
  }

  @Test
  void list_sizeZero_isRejected() {
    assertThatThrownBy(() -> listWith(0, 0)).isInstanceOf(AppException.class);
  }

  @Test
  void list_validBounds_delegatesToService() {
    when(tslService.listCertificates(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyInt(),
            anyInt()))
        .thenReturn(Page.empty());

    Object out = listWith(0, MAX_SIZE);

    assertThat(out).isNotNull();
    verify(tslService)
        .listCertificates(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            anyInt(),
            anyInt());
  }
}

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
package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyOverrideApplierTest {

  private final PolicyOverrideApplier applier = new PolicyOverrideApplier();

  @Test
  void disable_revocation_sets_level_ignore() {
    String xml =
        """
        <ConstraintsParameters xmlns="http://dss.esig.europa.eu/validation/policy">
          <SignatureConstraints>
            <BasicSignatureConstraints>
              <RevocationDataAvailable Level="FAIL"/>
            </BasicSignatureConstraints>
          </SignatureConstraints>
        </ConstraintsParameters>
        """;
    String modified = applier.apply(xml, Map.of("checkRevocation", false));
    assertThat(modified).contains("RevocationDataAvailable Level=\"IGNORE\"");
  }
}

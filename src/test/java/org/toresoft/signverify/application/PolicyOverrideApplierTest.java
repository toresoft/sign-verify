package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyOverrideApplierTest {

  private final PolicyOverrideApplier applier = new PolicyOverrideApplier();

  @Test
  void disable_revocation_sets_level_ignore() {
    String xml = """
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

package org.toresoft.signverify.adapter.dss;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.port.ReportType;
import org.toresoft.signverify.domain.port.ValidationRequest;

@SpringBootTest
@ActiveProfiles("test")
class DssValidatorAdapterTest {

  @Autowired private DssValidatorAdapter adapter;
  @Autowired private ObjectMapper om;

  @Test
  void parses_error_for_garbage_input() {
    byte[] bogus = "not a signature".getBytes();
    String policy =
        "<ConstraintsParameters xmlns=\"http://dss.esig.europa.eu/validation/policy\"/>";
    assertThatThrownBy(
            () ->
                adapter.validate(
                    new ValidationRequest(bogus, "x.pdf", policy, Set.of(ReportType.SIMPLE))))
        .isInstanceOf(AppException.class)
        .hasMessageContaining("Unprocessable");
  }
}

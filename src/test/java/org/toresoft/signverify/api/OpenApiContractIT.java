package org.toresoft.signverify.api;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.toresoft.signverify.application.TslService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiContractIT {

  @Autowired private MockMvc mvc;

  // The TslReadiness health indicator pulls TslService.isReady(); in the test
  // environment the TSL pipeline is skipped (startup-mode=SKIP), so isReady()
  // is false and the aggregate /actuator/health returns 503. Contract test must
  // assert against the spec, so we stub isReady() to true here.
  @MockitoBean private TslService tslService;

  @Test
  void health_satisfies_openapi() throws Exception {
    when(tslService.isReady()).thenReturn(true);

    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(openApi().isValid("openapi/openapi.yaml"));
  }
}

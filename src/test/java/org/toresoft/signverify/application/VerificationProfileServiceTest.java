package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.domain.model.Role;
import org.toresoft.signverify.persistence.VerificationProfileRepository;
import org.toresoft.signverify.security.Principal;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VerificationProfileServiceTest {

  @Autowired private VerificationProfileService service;
  @Autowired private VerificationProfileRepository repo;

  private final Principal admin =
      new Principal(PrincipalType.API_KEY, "tester", Role.PRIVILEGED, "tester");

  @Test
  void cannot_delete_default() {
    var def = repo.findByIsDefaultTrue().orElseThrow();
    assertThatThrownBy(() -> service.delete(def.getId(), admin)).isInstanceOf(AppException.class);
  }

  @Test
  void set_default_swaps() {
    var oldDefault = repo.findByIsDefaultTrue().orElseThrow();
    var p = service.create("X", "x", ProfilePreset.STRICT, null, admin);
    var newDef = service.setDefault(p.getId(), admin);

    assertThat(newDef.getIsDefault()).isTrue();
    assertThat(repo.findById(oldDefault.getId()).orElseThrow().getIsDefault()).isFalse();
  }
}

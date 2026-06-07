package org.toresoft.signverify.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.exception.AppException;
import org.toresoft.signverify.domain.model.ProfilePreset;
import org.toresoft.signverify.persistence.VerificationProfileRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VerificationProfileServiceTest {

  @Autowired private VerificationProfileService service;
  @Autowired private VerificationProfileRepository repo;

  @Test
  void cannot_delete_default() {
    var def = repo.findByIsDefaultTrue().orElseThrow();
    assertThatThrownBy(() -> service.delete(def.getId()))
        .isInstanceOf(AppException.class);
  }

  @Test
  void set_default_swaps() {
    var oldDefault = repo.findByIsDefaultTrue().orElseThrow();
    var p = service.create("X", "x", ProfilePreset.STRICT, null);
    var newDef = service.setDefault(p.getId());

    assertThat(newDef.getIsDefault()).isTrue();
    assertThat(repo.findById(oldDefault.getId()).orElseThrow().getIsDefault()).isFalse();
  }
}

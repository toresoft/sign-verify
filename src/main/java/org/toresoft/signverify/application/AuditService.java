package org.toresoft.signverify.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.persistence.AuditLogRepository;
import org.toresoft.signverify.security.Principal;

@Service
public class AuditService {
  private final AuditLogRepository repo;
  private final ObjectMapper om;

  public AuditService(AuditLogRepository repo, ObjectMapper om) {
    this.repo = repo;
    this.om = om;
  }

  public void log(
      Principal actor,
      String action,
      String targetType,
      String targetId,
      boolean success,
      Map<String, Object> details) {
    AuditLog a = new AuditLog();
    a.setId(UUID.randomUUID());
    a.setOccurredAt(Instant.now());
    a.setPrincipalType(actor == null ? PrincipalType.SYSTEM : actor.type());
    a.setPrincipalId(actor == null ? "system" : actor.id());
    a.setAction(action);
    a.setTargetType(targetType);
    a.setTargetId(targetId);
    a.setSuccess(success);
    try {
      a.setDetails(details == null ? null : om.writeValueAsString(details));
    } catch (Exception ignored) {
      a.setDetails(null);
    }
    repo.save(a);
  }
}

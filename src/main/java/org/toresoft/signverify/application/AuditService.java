package org.toresoft.signverify.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.config.AuditProperties;
import org.toresoft.signverify.domain.model.AuditLog;
import org.toresoft.signverify.domain.model.PrincipalType;
import org.toresoft.signverify.persistence.AuditLogRepository;
import org.toresoft.signverify.security.Principal;

/**
 * Application-level service that records audit events.
 *
 * <p>Public entry points ({@link #log}) are synchronous and perform the {@code enabled} guard on
 * the calling thread, avoiding an unnecessary async dispatch when audit is disabled. They delegate
 * to {@link #writeAsync} which is {@code @Async @Transactional(REQUIRES_NEW)} and runs on the
 * dedicated {@code auditExecutor} worker.
 *
 * <p>The {@code enabled} guard runs on the calling thread so that disabled audit produces zero
 * executor traffic. The MDC context ({@code clientIp}, {@code requestId}) set by {@code
 * RequestContextFilter} is propagated to the worker thread by {@code
 * AuditExecutorConfig.MdcTaskDecorator}.
 *
 * <p>This service is <strong>best-effort</strong>: any failure (DB down, JSON serialization, full
 * queue) is logged at WARN and swallowed so the audited business action is never blocked.
 */
@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  /** MDC key populated by {@code RequestContextFilter}. */
  static final String MDC_CLIENT_IP = "clientIp";

  /** MDC key populated by {@code RequestContextFilter}. */
  static final String MDC_REQUEST_ID = "requestId";

  private final AuditLogRepository repo;
  private final ObjectMapper om;
  private final AuditProperties props;

  /**
   * Self-reference routed through the Spring AOP proxy so that internal calls to {@link
   * #writeAsync} go through the proxy and honor {@code @Async} and {@code @Transactional}. Without
   * this, a direct {@code this.writeAsync()} call would bypass the proxy and execute synchronously
   * on the calling thread.
   */
  @Autowired @Lazy private AuditService self;

  public AuditService(AuditLogRepository repo, ObjectMapper om, AuditProperties props) {
    this.repo = repo;
    this.om = om;
    this.props = props;
  }

  /**
   * Records an audit event. Uses the actor's {@link PrincipalType} and id when present, otherwise
   * the row is attributed to {@code SYSTEM / system}.
   *
   * <p>The {@code enabled} check runs on the calling thread so that disabled audit produces zero
   * executor traffic. When enabled, the write is dispatched to the async executor.
   */
  public void log(
      Principal actor,
      String action,
      String targetType,
      String targetId,
      boolean success,
      Map<String, Object> details) {
    if (!props.isEnabled()) {
      return;
    }
    PrincipalType pType = actor == null ? PrincipalType.SYSTEM : actor.type();
    String pId = actor == null ? "system" : actor.id();
    self.writeAsync(pType, pId, action, targetType, targetId, success, enrich(details));
  }

  /**
   * Overload for contexts where the full {@link Principal} is not available (workers, schedulers,
   * test fixtures).
   */
  public void log(
      PrincipalType principalType,
      String principalId,
      String action,
      String targetType,
      String targetId,
      boolean success,
      Map<String, Object> details) {
    if (!props.isEnabled()) {
      return;
    }
    self.writeAsync(
        principalType == null ? PrincipalType.SYSTEM : principalType,
        principalId == null ? "system" : principalId,
        action,
        targetType,
        targetId,
        success,
        enrich(details));
  }

  /**
   * Asynchronous write on the dedicated {@code auditExecutor} in its own transaction. The {@code
   * enabled} guard has already been evaluated by the synchronous entry point, so this method always
   * attempts the write.
   *
   * <p>Must be called via the AOP proxy ({@code self.writeAsync}) — not via {@code this.writeAsync}
   * — so that both {@code @Async} and {@code @Transactional(REQUIRES_NEW)} take effect.
   */
  @Async("auditExecutor")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void writeAsync(
      PrincipalType pType,
      String pId,
      String action,
      String targetType,
      String targetId,
      boolean success,
      Map<String, Object> details) {
    AuditLog a = new AuditLog();
    a.setId(UUID.randomUUID());
    a.setOccurredAt(Instant.now());
    a.setPrincipalType(pType);
    a.setPrincipalId(pId);
    a.setAction(action);
    a.setTargetType(targetType);
    a.setTargetId(targetId);
    a.setSuccess(success);
    a.setIpAddress(MDC.get(MDC_CLIENT_IP));
    a.setDetails(serialize(details));
    try {
      repo.save(a);
    } catch (RuntimeException ex) {
      // Best-effort: never let an audit failure cascade into the business path.
      log.warn(
          "audit write failed: action={} targetType={} targetId={}",
          action,
          targetType,
          targetId,
          ex);
    }
  }

  /**
   * If a {@code requestId} is in the MDC and the caller did not already include one, propagate it
   * into the persisted details so the audit row can be correlated with logs and the response.
   */
  private Map<String, Object> enrich(Map<String, Object> details) {
    String requestId = MDC.get(MDC_REQUEST_ID);
    if (requestId == null || requestId.isBlank()) {
      return details;
    }
    if (details != null && details.containsKey("requestId")) {
      return details;
    }
    Map<String, Object> merged = new HashMap<>();
    if (details != null) merged.putAll(details);
    merged.put("requestId", requestId);
    return merged;
  }

  private String serialize(Map<String, Object> details) {
    if (details == null) return null;
    try {
      return om.writeValueAsString(details);
    } catch (JsonProcessingException e) {
      log.warn("audit details serialization failed; persisting without details", e);
      return null;
    } catch (RuntimeException e) {
      // Jackson sometimes wraps the cause in non-JPE RuntimeExceptions (e.g. via custom
      // serializers). We still want to fall back to null instead of failing the audit write.
      log.warn("audit details serialization failed unexpectedly; persisting without details", e);
      return null;
    }
  }
}

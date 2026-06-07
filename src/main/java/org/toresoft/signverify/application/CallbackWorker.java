package org.toresoft.signverify.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.toresoft.signverify.domain.model.*;
import org.toresoft.signverify.domain.port.CallbackDispatcherPort;
import org.toresoft.signverify.domain.port.DocumentStoragePort;
import org.toresoft.signverify.domain.port.SecretCipherPort;
import org.toresoft.signverify.persistence.ValidationJobRepository;

@Component
public class CallbackWorker {

  private static final Logger log = LoggerFactory.getLogger(CallbackWorker.class);

  private final ValidationJobRepository repo;
  private final CallbackDispatcherPort dispatcher;
  private final SecretCipherPort cipher;
  private final DocumentStoragePort storage;
  private final ObjectMapper om;
  private final int maxAttempts;
  private final List<Duration> backoff;
  private final Set<Integer> successCodes;
  private final Set<Integer> nonRetryableCodes;

  public CallbackWorker(
      ValidationJobRepository repo,
      CallbackDispatcherPort dispatcher,
      SecretCipherPort cipher,
      DocumentStoragePort storage,
      ObjectMapper om,
      @Value("${app.callback.max-attempts}") int maxAttempts,
      @Value("${app.callback.backoff}") List<Duration> backoff,
      @Value("${app.callback.success-statuses}") List<Integer> success,
      @Value("${app.callback.non-retryable-statuses}") List<Integer> nonRetry) {
    this.repo = repo;
    this.dispatcher = dispatcher;
    this.cipher = cipher;
    this.storage = storage;
    this.om = om;
    this.maxAttempts = maxAttempts;
    this.backoff = backoff;
    this.successCodes = new HashSet<>(success);
    this.nonRetryableCodes = new HashSet<>(nonRetry);
  }

  @Scheduled(fixedDelayString = "${app.callback.worker.poll-interval}")
  @SchedulerLock(name = "callbackDispatch", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  public void poll() {
    var due = repo.findCallbacksDue(Instant.now(), maxAttempts, PageRequest.of(0, 16));
    for (ValidationJob j : due) {
      try {
        dispatch(j.getId());
      } catch (Exception e) {
        log.error("callback dispatch error on job {}", j.getId(), e);
      }
    }
  }

  @Transactional
  public void dispatch(UUID id) {
    ValidationJob job = repo.findById(id).orElse(null);
    if (job == null || job.getCallbackUrl() == null) return;
    if (job.getNextCallbackAt() == null || job.getNextCallbackAt().isAfter(Instant.now())) return;

    String secret = cipher.decrypt(job.getCallbackSecretCipher());
    byte[] body = buildBody(job);
    String deliveryId = UUID.randomUUID().toString();
    int attempt = job.getCallbackAttempts() + 1;

    var res =
        dispatcher.dispatch(
            job.getCallbackUrl(),
            job.getCallbackAlgorithm(),
            secret,
            body,
            job.getId().toString(),
            deliveryId,
            attempt);
    if (res.success(successCodes)) {
      job.setStatus(JobStatus.DELIVERED);
      job.setDeliveredAt(Instant.now());
    } else if (res.nonRetryable(nonRetryableCodes)) {
      job.setStatus(JobStatus.DELIVERY_FAILED);
      job.setNextCallbackAt(null);
      job.setLastCallbackError("status=" + res.statusCode());
    } else if (attempt >= maxAttempts) {
      job.setStatus(JobStatus.DELIVERY_FAILED);
      job.setNextCallbackAt(null);
      job.setLastCallbackError(
          res.errorMessage() == null ? "status=" + res.statusCode() : res.errorMessage());
    } else {
      Duration d = backoff.get(Math.min(attempt - 1, backoff.size() - 1));
      job.setNextCallbackAt(Instant.now().plus(d));
      job.setLastCallbackError(
          res.errorMessage() == null ? "status=" + res.statusCode() : res.errorMessage());
    }
    job.setCallbackAttempts(attempt);
    repo.save(job);
  }

  private byte[] buildBody(ValidationJob job) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("jobId", job.getId());
      body.put("status", job.getStatus().name());
      if (job.getStatus() == JobStatus.COMPLETED && job.getResultPath() != null) {
        byte[] r = storage.read(job.getResultPath());
        body.put("result", om.readTree(r));
      } else if (job.getStatus() == JobStatus.FAILED) {
        String msg = job.getErrorMessage() == null ? "unknown" : job.getErrorMessage();
        body.put("error", msg);
      }
      return om.writeValueAsBytes(body);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

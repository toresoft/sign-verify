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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.toresoft.signverify.domain.exception.Errors;
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
  private final Executor callbackExecutor;
  private final Semaphore capacity;
  private final Duration claimTimeout;

  public CallbackWorker(
      ValidationJobRepository repo,
      CallbackDispatcherPort dispatcher,
      SecretCipherPort cipher,
      DocumentStoragePort storage,
      ObjectMapper om,
      @Value("${app.callback.max-attempts}") int maxAttempts,
      @Value("${app.callback.backoff}") List<Duration> backoff,
      @Value("${app.callback.success-statuses}") List<Integer> success,
      @Value("${app.callback.non-retryable-statuses}") List<Integer> nonRetry,
      @Qualifier("callbackExecutor") Executor callbackExecutor,
      @Value("${app.callback.workers}") int workers,
      @Value("${app.callback.worker.claim-timeout}") Duration claimTimeout) {
    this.repo = repo;
    this.dispatcher = dispatcher;
    this.cipher = cipher;
    this.storage = storage;
    this.om = om;
    this.maxAttempts = maxAttempts;
    this.backoff = backoff;
    this.successCodes = new HashSet<>(success);
    this.nonRetryableCodes = new HashSet<>(nonRetry);
    this.callbackExecutor = callbackExecutor;
    this.capacity = new Semaphore(workers);
    this.claimTimeout = claimTimeout;
  }

  @Scheduled(fixedDelayString = "${app.callback.worker.poll-interval}")
  @SchedulerLock(name = "callbackDispatch", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
  public void poll() {
    // Backpressure: only claim as many callbacks as there is free dispatch capacity.
    int free = capacity.availablePermits();
    if (free == 0) return;
    Instant now = Instant.now();
    var due = repo.findCallbacksDue(now, maxAttempts, PageRequest.of(0, free));
    Instant parkUntil = now.plus(claimTimeout);
    for (ValidationJob j : due) {
      if (!capacity.tryAcquire()) break;
      UUID id = j.getId();
      // Atomic claim: park nextCallbackAt into the future so no concurrent poll/instance re-picks
      // this callback while its (offloaded) HTTP dispatch is in flight. Only the winner dispatches.
      if (repo.claimCallback(id, now, parkUntil, maxAttempts) == 0) {
        capacity.release();
        continue;
      }
      try {
        // Offload the blocking outbound HTTP call so the shared @Scheduled thread returns at once.
        callbackExecutor.execute(
            () -> {
              try {
                deliverClaimed(id);
              } catch (Exception e) {
                log.error("callback dispatch error on job {}", id, e);
              } finally {
                capacity.release();
              }
            });
      } catch (RejectedExecutionException rex) {
        capacity.release();
        log.warn("callback executor saturated; deferring job {} to next poll", id);
        break;
      }
    }
  }

  /**
   * Dispatch entry point that revalidates the due-window guard itself (for direct/manual calls).
   * The poll path uses {@link #deliverClaimed(UUID)} instead, because it has already claimed the
   * row by parking {@code nextCallbackAt} forward — which would make this guard reject it.
   */
  public void dispatch(UUID id) {
    ValidationJob job = repo.findById(id).orElse(null);
    if (job == null || job.getCallbackUrl() == null) return;
    if (job.getNextCallbackAt() == null || job.getNextCallbackAt().isAfter(Instant.now())) return;
    deliver(job);
  }

  /**
   * Loads a job already claimed by {@link #poll()} and delivers it, skipping the due-window guard.
   */
  void deliverClaimed(UUID id) {
    ValidationJob job = repo.findById(id).orElse(null);
    if (job == null || job.getCallbackUrl() == null) return;
    deliver(job);
  }

  // Deliberately NOT @Transactional: the method performs an outbound HTTP call; holding a DB
  // transaction across it would pin a connection for the whole callback timeout. The single
  // repo.save at the end commits on its own.
  private void deliver(ValidationJob job) {
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
        body.put("errorCode", job.getErrorCode() == null ? Errors.INTERNAL : job.getErrorCode());
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

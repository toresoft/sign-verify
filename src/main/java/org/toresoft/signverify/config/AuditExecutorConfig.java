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
package org.toresoft.signverify.config;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Wires the dedicated executor used by {@code @Async("auditExecutor")} on {@code AuditService}.
 *
 * <p>The executor is a small bounded pool with a queue limit. On saturation the rejection handler
 * logs a WARN and drops the task: throwing (the default AbortPolicy) would surface a {@code
 * TaskRejectedException} on the business thread at {@code @Async} dispatch time, and caller-runs
 * would block that thread — both contradict the best-effort contract of the audit subsystem. A
 * small pool + bounded queue also keeps memory bounded when the DB is slow.
 *
 * <p>The {@link MdcTaskDecorator} propagates the MDC context (specifically {@code clientIp} and
 * {@code requestId} populated by {@code RequestContextFilter}) from the calling thread to the
 * worker thread so the audit row can record the client IP even though the write happens off-thread.
 * The decorator is intentionally a no-op (clears the MDC) when the submitter has no MDC set, so
 * scheduled jobs that run on threads without a request scope do not leak context across tasks.
 */
@Configuration
@EnableConfigurationProperties(AuditProperties.class)
public class AuditExecutorConfig {

  private static final Logger log = LoggerFactory.getLogger(AuditExecutorConfig.class);

  /** Thread name prefix; useful in logs and {@code jstack}. */
  private static final String THREAD_NAME_PREFIX = "audit-";

  @Bean(name = "auditExecutor")
  public ThreadPoolTaskExecutor auditExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    // Small pool: writes are tiny single-row inserts, but we still want concurrency.
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(4);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(10);
    executor.setTaskDecorator(new MdcTaskDecorator());
    // On saturation (all threads busy AND queue full), drop the audit task and log a WARN instead
    // of throwing. The default AbortPolicy would raise a TaskRejectedException on the *calling*
    // (business) thread at @Async dispatch time — before AuditService's try/catch, which runs on
    // the worker thread, can intercept it. Discarding honors the best-effort contract: an audit
    // write must never fail or block the business action it is recording.
    executor.setRejectedExecutionHandler(
        (runnable, pool) ->
            log.warn(
                "audit executor saturated (active={}, queue={}); dropping audit write",
                pool.getActiveCount(),
                pool.getQueue().size()));
    executor.initialize();
    return executor;
  }

  /**
   * Copies the caller's MDC into the worker thread before the task runs, and clears the MDC after
   * the task returns so the worker thread does not leak the previous task's context to the next
   * one. The original caller's MDC is untouched.
   */
  static final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
      // Snapshot on the calling thread (the request thread that invokes @Async).
      Map<String, String> contextMap = MDC.getCopyOfContextMap();
      return () -> {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
          if (contextMap != null) {
            MDC.setContextMap(contextMap);
          } else {
            MDC.clear();
          }
          runnable.run();
        } finally {
          if (previous != null) {
            MDC.setContextMap(previous);
          } else {
            MDC.clear();
          }
        }
      };
    }
  }
}

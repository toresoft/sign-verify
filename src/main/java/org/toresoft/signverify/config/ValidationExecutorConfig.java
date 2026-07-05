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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Dedicated executor that runs the (potentially minutes-long) DSS validation of async jobs OFF the
 * shared {@code @Scheduled} thread. {@code ValidationWorker.poll()} only claims and submits jobs;
 * without this the validation would run inline on the single scheduler thread and starve every
 * other scheduled task (callback dispatch, TSL refresh, cleanup sweeps).
 *
 * <p>Sized by the previously-unused {@code app.async.workers} property (default 4) — the intended
 * validation concurrency. {@code ValidationWorker} gates submissions with a semaphore of the same
 * size, so in-flight tasks never exceed the pool; the queue is only a small slack buffer to absorb
 * the release/submit race, and the pool should therefore never reject.
 */
@Configuration
public class ValidationExecutorConfig {

  private static final String THREAD_NAME_PREFIX = "validation-";

  @Bean(name = "validationExecutor")
  public ThreadPoolTaskExecutor validationExecutor(@Value("${app.async.workers}") int workers) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(workers);
    executor.setMaxPoolSize(workers);
    executor.setQueueCapacity(workers);
    executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    // Drain in-flight validations on shutdown instead of interrupting them mid-DSS-call; a job left
    // in RUNNING by a hard kill is recovered by ValidationWorker's stale-RUNNING sweep.
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}

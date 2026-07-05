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
 * Dedicated executor for outbound callback (webhook) dispatch, so the blocking HTTP call runs OFF
 * the shared {@code @Scheduled} thread. {@code CallbackWorker.poll()} atomically claims due
 * callbacks and submits them here; the scheduler thread returns immediately instead of serially
 * blocking on up to N callback timeouts. Sized by {@code app.callback.workers} (default 4); {@code
 * CallbackWorker} gates submissions with a same-sized semaphore so in-flight dispatches never
 * exceed the pool and the small slack queue is never exercised.
 */
@Configuration
public class CallbackExecutorConfig {

  private static final String THREAD_NAME_PREFIX = "callback-";

  @Bean(name = "callbackExecutor")
  public ThreadPoolTaskExecutor callbackExecutor(@Value("${app.callback.workers}") int workers) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(workers);
    executor.setMaxPoolSize(workers);
    executor.setQueueCapacity(workers);
    executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}

package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async execution worker pool.
 */
@Configuration
@EnableAsync
public class ExecutorConfig {

    /**
     * Thread pool for execution workers.
     * Workers poll the Redis queue and process submissions.
     */
    @Bean(name = "executionWorkerExecutor")
    public Executor executionWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - always running workers
        executor.setCorePoolSize(5);

        // Max pool size - scale up during peak load
        executor.setMaxPoolSize(10);

        // Queue capacity - pending tasks before rejection
        executor.setQueueCapacity(100);

        // Thread naming for debugging
        executor.setThreadNamePrefix("execution-worker-");

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}

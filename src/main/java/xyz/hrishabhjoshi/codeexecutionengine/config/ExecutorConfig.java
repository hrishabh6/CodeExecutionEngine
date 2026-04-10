package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async execution worker pool.
 */
@Configuration
@EnableAsync
public class ExecutorConfig {

    @Value("${execution.worker.count:5}")
    private int workerCount;

    @Value("${execution.worker.queue-capacity:100}")
    private int queueCapacity;

    /**
     * Thread pool for execution workers.
     * Workers poll the Redis queue and process submissions.
     */
    @Bean(name = "executionWorkerExecutor")
    public Executor executionWorkerExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Keep concurrency explicitly bounded by workerCount.
        executor.setCorePoolSize(workerCount);
        executor.setMaxPoolSize(workerCount);
        executor.setQueueCapacity(queueCapacity);

        // Thread naming for debugging
        executor.setThreadNamePrefix("execution-worker-");

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}

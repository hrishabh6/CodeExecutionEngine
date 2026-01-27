package xyz.hrishabhjoshi.codeexecutionengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import xyz.hrishabhjoshi.codeexecutionengine.model.ExecutionMetrics;

import java.util.Optional;

/**
 * Repository for ExecutionMetrics entity operations.
 */
@Repository
public interface ExecutionMetricsRepository extends JpaRepository<ExecutionMetrics, Long> {

    /**
     * Find metrics by submission ID.
     */
    Optional<ExecutionMetrics> findBySubmissionId(String submissionId);

    /**
     * Get average execution time across all submissions.
     */
    @Query("SELECT AVG(m.executionMs) FROM ExecutionMetrics m WHERE m.executionMs IS NOT NULL")
    Double getAverageExecutionTime();

    /**
     * Get average queue wait time.
     */
    @Query("SELECT AVG(m.queueWaitMs) FROM ExecutionMetrics m WHERE m.queueWaitMs IS NOT NULL")
    Double getAverageQueueWaitTime();
}

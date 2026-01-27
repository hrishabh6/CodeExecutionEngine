package xyz.hrishabhjoshi.codeexecutionengine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;
import xyz.hrishabhjoshi.codeexecutionengine.repository.ExecutionMetricsRepository;

import java.util.Optional;

/**
 * Service for retrieving submission status from Redis.
 * NOTE: CXE only uses Redis for status - it does NOT access the submission table
 * (that's SubmissionService's responsibility).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionStatusService {

    private final ExecutionQueueService queueService;
    private final ExecutionMetricsRepository metricsRepository;

    /**
     * Get current status from Redis.
     */
    public Optional<SubmissionStatusDto> getStatus(String submissionId) {
        Optional<SubmissionStatusDto> redisStatus = queueService.getRedisStatus(submissionId);
        if (redisStatus.isPresent()) {
            SubmissionStatusDto status = redisStatus.get();
            // Add queue position if still queued
            if ("QUEUED".equals(status.getStatus())) {
                status.setQueuePosition(queueService.getQueuePosition(submissionId));
            }
            return Optional.of(status);
        }
        // Not found in Redis - status may have expired
        log.debug("Status not found in Redis for submission {}", submissionId);
        return Optional.empty();
    }

    /**
     * Get full results including test case details from Redis.
     */
    public Optional<SubmissionStatusDto> getFullResults(String submissionId) {
        Optional<SubmissionStatusDto> redisStatus = queueService.getRedisStatus(submissionId);
        if (redisStatus.isPresent()) {
            return redisStatus;
        }
        // Not found in Redis - status may have expired
        log.debug("Results not found in Redis for submission {}", submissionId);
        return Optional.empty();
    }

    /**
     * Get count of active workers.
     */
    public int getActiveWorkerCount() {
        return ExecutionWorkerService.getActiveWorkerCount();
    }

    /**
     * Get average execution time from metrics.
     */
    public double getAverageExecutionTime() {
        Double avg = metricsRepository.getAverageExecutionTime();
        return avg != null ? avg : 0.0;
    }
}


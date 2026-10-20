package xyz.hrishabhjoshi.codeexecutionengine.service.helperservice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;
import xyz.hrishabhjoshi.codeexecutionengine.service.v2.ExecutionV2StatusStore;

import java.util.Optional;

/**
 * Service for retrieving submission status from Redis.
 * CXE only uses Redis for status - no DB access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionStatusService {

    private final ExecutionQueueService queueService;
    private final Optional<ExecutionV2StatusStore> v2StatusStore;

    /**
     * Get current status from Redis.
     */
    public Optional<SubmissionStatusDto> getStatus(String submissionId) {
        Optional<SubmissionStatusDto> redisStatus = findStatus(submissionId);
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
        Optional<SubmissionStatusDto> redisStatus = findStatus(submissionId);
        if (redisStatus.isPresent()) {
            return redisStatus;
        }
        // Not found in Redis - status may have expired
        log.debug("Results not found in Redis for submission {}", submissionId);
        return Optional.empty();
    }

    private Optional<SubmissionStatusDto> findStatus(String submissionId) {
        Optional<SubmissionStatusDto> legacy = queueService.getRedisStatus(submissionId);
        if (legacy.isPresent()) {
            return legacy;
        }
        return v2StatusStore.flatMap(store -> store.getStatus(submissionId));
    }

    /**
     * Get count of active workers.
     */
    public int getActiveWorkerCount() {
        return ExecutionWorkerService.getActiveWorkerCount();
    }

    /**
     * Get average execution time.
     * Without DB, we return 0 - metrics tracking is not CXE's responsibility.
     */
    public double getAverageExecutionTime() {
        return 0.0;  // No DB metrics - SubmissionService handles this
    }
}

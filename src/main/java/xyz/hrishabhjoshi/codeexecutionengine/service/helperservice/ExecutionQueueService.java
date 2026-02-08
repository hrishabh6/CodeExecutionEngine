package xyz.hrishabhjoshi.codeexecutionengine.service.helperservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionRequest;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing the Redis execution queue and submission status.
 * NOTE: CXE does NOT persist to Submission table - that's SubmissionService's
 * responsibility.
 * CXE only tracks execution status in Redis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionQueueService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${execution.queue.name:execution:queue}")
    private String queueName;

    @Value("${execution.queue.status-prefix:execution:status:}")
    private String statusPrefix;

    @Value("${execution.queue.status-ttl-seconds:3600}")
    private long statusTtl;

    /**
     * Add submission to queue for execution.
     * NOTE: CXE does NOT persist to Submission table - that's SubmissionService's
     * responsibility.
     * CXE only tracks execution status in Redis.
     *
     * @param request The execution request
     * @return The submission ID
     */
    public String enqueue(ExecutionRequest request) {
        log.info("=== [QUEUE] enqueue() called ===");

        // Generate submission ID if not provided
        String submissionId = request.getSubmissionId();
        if (submissionId == null || submissionId.isEmpty()) {
            submissionId = UUID.randomUUID().toString();
            request.setSubmissionId(submissionId);
            log.info("[QUEUE] Generated new submissionId={}", submissionId);
        } else {
            log.info("[QUEUE] Using provided submissionId={}", submissionId);
        }

        // Set initial status in Redis (fast polling)
        log.info("[QUEUE] Setting initial QUEUED status in Redis...");
        setRedisStatus(submissionId, SubmissionStatusDto.builder()
                .submissionId(submissionId)
                .status("QUEUED")
                .queuedAt(System.currentTimeMillis())
                .build());
        log.info("[QUEUE] Redis status set successfully");

        // Add to queue (LPUSH = left push, workers BRPOP from right)
        log.info("[QUEUE] LPUSH to queue '{}' for submissionId={}", queueName, submissionId);
        redisTemplate.opsForList().leftPush(queueName, request);

        Long queueSize = redisTemplate.opsForList().size(queueName);
        log.info("[QUEUE] Enqueued successfully. Queue size is now: {}", queueSize);

        return submissionId;
    }

    /**
     * Dequeue next submission for processing (blocking).
     *
     * @param timeoutSeconds Time to wait for an item
     * @return The next request or null if timeout
     */
    public ExecutionRequest dequeue(long timeoutSeconds) {
        log.trace("[QUEUE] Worker calling BRPOP on '{}' with timeout={}s...", queueName, timeoutSeconds);

        Object result = redisTemplate.opsForList()
                .rightPop(queueName, timeoutSeconds, TimeUnit.SECONDS);

        if (result == null) {
            log.trace("[QUEUE] BRPOP timeout - no items in queue");
            return null;
        }

        log.info("[QUEUE] BRPOP got item from queue, result type={}", result.getClass().getSimpleName());

        // Handle potential type conversion from Redis
        if (result instanceof ExecutionRequest) {
            ExecutionRequest req = (ExecutionRequest) result;
            log.info("[QUEUE] Dequeued submissionId={} (direct cast)", req.getSubmissionId());
            return req;
        }

        // If it came back as a LinkedHashMap, convert it
        try {
            String json = objectMapper.writeValueAsString(result);
            log.debug("[QUEUE] Converting LinkedHashMap to ExecutionRequest via JSON");
            ExecutionRequest req = objectMapper.readValue(json, ExecutionRequest.class);
            log.info("[QUEUE] Dequeued submissionId={} (JSON conversion)", req.getSubmissionId());
            return req;
        } catch (JsonProcessingException e) {
            log.error("[QUEUE] CRITICAL: Failed to deserialize queue item: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get current queue size.
     */
    public Long getQueueSize() {
        Long size = redisTemplate.opsForList().size(queueName);
        return size != null ? size : 0L;
    }

    /**
     * Get position in queue for a submission.
     *
     * @param submissionId The submission ID
     * @return Position (1-based) or null if not in queue
     */
    public Integer getQueuePosition(String submissionId) {
        List<Object> queue = redisTemplate.opsForList().range(queueName, 0, -1);
        if (queue == null)
            return null;

        for (int i = 0; i < queue.size(); i++) {
            try {
                String json = objectMapper.writeValueAsString(queue.get(i));
                ExecutionRequest req = objectMapper.readValue(json, ExecutionRequest.class);
                if (submissionId.equals(req.getSubmissionId())) {
                    return i + 1;
                }
            } catch (JsonProcessingException e) {
                log.error("Error parsing queue item", e);
            }
        }
        return null; // Not in queue (already processing or completed)
    }

    /**
     * Estimate wait time based on queue size.
     *
     * @return Estimated wait time in milliseconds
     */
    public Long getEstimatedWaitTime() {
        Long queueSize = getQueueSize();
        if (queueSize == 0)
            return 0L;
        // Assume average 3 seconds per submission
        return queueSize * 3000;
    }

    /**
     * Cancel a pending submission.
     * NOTE: CXE only updates Redis status - SubmissionService owns the submission
     * table.
     *
     * @param submissionId The submission ID
     * @return true if cancelled, false if not found or already processing
     */
    public boolean cancelSubmission(String submissionId) {
        List<Object> queue = redisTemplate.opsForList().range(queueName, 0, -1);
        if (queue == null)
            return false;

        for (Object item : queue) {
            try {
                String json = objectMapper.writeValueAsString(item);
                ExecutionRequest req = objectMapper.readValue(json, ExecutionRequest.class);
                if (submissionId.equals(req.getSubmissionId())) {
                    // Remove from queue
                    redisTemplate.opsForList().remove(queueName, 1, item);

                    // Update status in Redis only
                    setRedisStatus(submissionId, SubmissionStatusDto.builder()
                            .submissionId(submissionId)
                            .status("CANCELLED")
                            .completedAt(System.currentTimeMillis())
                            .build());

                    log.info("Cancelled submission {} from queue", submissionId);
                    return true;
                }
            } catch (JsonProcessingException e) {
                log.error("Error parsing queue item", e);
            }
        }
        return false;
    }

    /**
     * Set status in Redis for fast polling.
     */
    public void setRedisStatus(String submissionId, SubmissionStatusDto status) {
        String key = statusPrefix + submissionId;
        redisTemplate.opsForValue().set(key, status, statusTtl, TimeUnit.SECONDS);
    }

    /**
     * Get status from Redis.
     */
    public Optional<SubmissionStatusDto> getRedisStatus(String submissionId) {
        String key = statusPrefix + submissionId;
        Object status = redisTemplate.opsForValue().get(key);

        if (status == null) {
            return Optional.empty();
        }

        if (status instanceof SubmissionStatusDto) {
            return Optional.of((SubmissionStatusDto) status);
        }

        // Convert from LinkedHashMap if needed
        try {
            String json = objectMapper.writeValueAsString(status);
            return Optional.of(objectMapper.readValue(json, SubmissionStatusDto.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize status: {}", e.getMessage());
            return Optional.empty();
        }
    }
}

package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.execution.ExecutionRequestRejectedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexityProfileQueueService {

    /**
     * Atomically: reject if already accepted, reject if queue full, else set accept + status + job payload + LPUSH.
     * KEYS[1]=accept, [2]=status, [3]=job, [4]=result, [5]=queue
     * ARGV[1]=statusJson, [2]=jobJson, [3]=executionId, [4]=statusTtlSec, [5]=jobTtlSec, [6]=queueCapacity
     * Returns: 1=new accept, 0=already accepted, -1=queue full
     */
    private static final RedisScript<Long> ATOMIC_ACCEPT_AND_ENQUEUE = new DefaultRedisScript<>(
            """
            if redis.call('exists', KEYS[1]) == 1 then
              if redis.call('exists', KEYS[2]) == 1
                  or redis.call('exists', KEYS[3]) == 1
                  or redis.call('exists', KEYS[4]) == 1 then
                return 0
              end
              redis.call('del', KEYS[1])
            end
            if redis.call('exists', KEYS[2]) == 1 then return 0 end
            if redis.call('exists', KEYS[3]) == 1 then return 0 end
            if redis.call('exists', KEYS[4]) == 1 then return 0 end
            local depth = redis.call('llen', KEYS[5])
            if depth >= tonumber(ARGV[6]) then return -1 end
            redis.call('set', KEYS[1], '1', 'EX', ARGV[4])
            redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[4])
            redis.call('set', KEYS[3], ARGV[2], 'EX', ARGV[5])
            redis.call('lpush', KEYS[5], ARGV[3])
            return 1
            """,
            Long.class);

    public enum EnqueueOutcome {
        ACCEPTED_NEW,
        ALREADY_ACCEPTED,
        QUEUE_FULL
    }

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ComplexityProfileExecutionProperties properties;

    public boolean hasAcceptedExecution(String executionId) {
        if (executionId == null || executionId.isBlank()) {
            return false;
        }
        String acceptKey = acceptKey(executionId);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(acceptKey))) {
            if (getStatus(executionId).isPresent() || getResult(executionId).isPresent()) {
                return true;
            }
            String jobKey = properties.getQueue().getJobPayloadPrefix() + executionId;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(jobKey))) {
                return true;
            }
            stringRedisTemplate.delete(acceptKey);
        }
        String jobKey = properties.getQueue().getJobPayloadPrefix() + executionId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(jobKey))) {
            return true;
        }
        return getStatus(executionId).isPresent() || getResult(executionId).isPresent();
    }

    public String enqueue(ComplexityProfileJobPayload payload) {
        EnqueueOutcome outcome = tryEnqueue(payload);
        if (outcome == EnqueueOutcome.QUEUE_FULL) {
            throw new ExecutionRequestRejectedException("PROFILE_QUEUE_FULL", "Complexity profile queue is full");
        }
        return payload.getExecutionId();
    }

    public EnqueueOutcome tryEnqueue(ComplexityProfileJobPayload payload) {
        String executionId = payload.getExecutionId();
        if (executionId == null || executionId.isBlank()) {
            executionId = UUID.randomUUID().toString();
            payload.setExecutionId(executionId);
        }
        payload.setEnqueuedAtMs(System.currentTimeMillis());
        ComplexityProfilePollResponse queuedStatus = new ComplexityProfilePollResponse(
                executionId, "QUEUED", null, null, null, null, null, null, null, null, null);
        try {
            String statusJson = objectMapper.writeValueAsString(queuedStatus);
            String jobJson = objectMapper.writeValueAsString(payload);
            Long result = stringRedisTemplate.execute(
                    ATOMIC_ACCEPT_AND_ENQUEUE,
                    List.of(
                            acceptKey(executionId),
                            properties.getQueue().getStatusPrefix() + executionId,
                            properties.getQueue().getJobPayloadPrefix() + executionId,
                            properties.getQueue().getResultPrefix() + executionId,
                            properties.getQueue().getName()),
                    statusJson,
                    jobJson,
                    executionId,
                    String.valueOf(acceptMarkerTtlSeconds()),
                    String.valueOf(properties.getQueue().getQueuedJobMaxAgeSeconds()),
                    String.valueOf(properties.getWorker().getQueueCapacity()));
            if (result == null || result == 0L) {
                return EnqueueOutcome.ALREADY_ACCEPTED;
            }
            if (result < 0L) {
                return EnqueueOutcome.QUEUE_FULL;
            }
            return EnqueueOutcome.ACCEPTED_NEW;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize profile queue payload", e);
        }
    }

    public ComplexityProfileJobPayload dequeue(long timeoutSeconds) {
        Object raw = redisTemplate.opsForList().rightPop(
                properties.getQueue().getName(), timeoutSeconds, TimeUnit.SECONDS);
        if (raw == null) {
            return null;
        }
        String executionId = raw.toString();
        String jobKey = properties.getQueue().getJobPayloadPrefix() + executionId;
        Object stored = redisTemplate.opsForValue().get(jobKey);
        redisTemplate.delete(jobKey);
        if (stored == null) {
            log.info("[PROFILE-QUEUE] dropped stale or expired job executionId={}", executionId);
            expireAbandonedStatus(executionId);
            return null;
        }
        Optional<ComplexityProfileJobPayload> payload = deserialize(stored, ComplexityProfileJobPayload.class);
        if (payload.isEmpty()) {
            log.error("[PROFILE-QUEUE] failed to deserialize job payload for executionId={}", executionId);
            return null;
        }
        ComplexityProfileJobPayload job = payload.get();
        long maxAgeMs = properties.getQueue().getQueuedJobMaxAgeSeconds() * 1000L;
        if (job.getEnqueuedAtMs() > 0 && System.currentTimeMillis() - job.getEnqueuedAtMs() > maxAgeMs) {
            log.info("[PROFILE-QUEUE] dropped aged queued job executionId={}", executionId);
            expireAbandonedStatus(executionId);
            return null;
        }
        return job;
    }

    private void expireAbandonedStatus(String executionId) {
        setStatus(executionId, new ComplexityProfilePollResponse(
                executionId, "EXPIRED", null, null, null, null, null, null, null, "JOB_EXPIRED", "Queued profile job expired"));
    }

    public Long getQueueSize() {
        Long size = redisTemplate.opsForList().size(properties.getQueue().getName());
        return size == null ? 0L : size;
    }

    public void setStatus(String executionId, ComplexityProfilePollResponse status) {
        String key = properties.getQueue().getStatusPrefix() + executionId;
        redisTemplate.opsForValue().set(
                key,
                status,
                properties.getQueue().getStatusTtlSeconds(),
                TimeUnit.SECONDS);
    }

    public Optional<ComplexityProfilePollResponse> getStatus(String executionId) {
        Object value = redisTemplate.opsForValue().get(properties.getQueue().getStatusPrefix() + executionId);
        return deserialize(value, ComplexityProfilePollResponse.class);
    }

    public void stageJobPayloadForRunner(ComplexityProfileJobPayload payload) {
        String executionId = payload.getExecutionId();
        String jobKey = properties.getQueue().getJobPayloadPrefix() + executionId;
        redisTemplate.opsForValue().set(
                jobKey,
                payload,
                properties.getQueue().getQueuedJobMaxAgeSeconds(),
                TimeUnit.SECONDS);
    }

    public Optional<ComplexityProfileJobPayload> loadStagedJobPayload(String executionId) {
        String jobKey = properties.getQueue().getJobPayloadPrefix() + executionId;
        Object stored = redisTemplate.opsForValue().get(jobKey);
        return deserialize(stored, ComplexityProfileJobPayload.class);
    }

    public void deleteStagedJobPayload(String executionId) {
        redisTemplate.delete(properties.getQueue().getJobPayloadPrefix() + executionId);
    }

    public void storeResult(String executionId, ComplexityProfilePollResponse terminal) {
        String key = properties.getQueue().getResultPrefix() + executionId;
        redisTemplate.opsForValue().set(
                key,
                terminal,
                properties.getQueue().getResultTtlSeconds(),
                TimeUnit.SECONDS);
        setStatus(executionId, terminal);
    }

    public Optional<ComplexityProfilePollResponse> getResult(String executionId) {
        Object value = redisTemplate.opsForValue().get(properties.getQueue().getResultPrefix() + executionId);
        return deserialize(value, ComplexityProfilePollResponse.class);
    }

    private String acceptKey(String executionId) {
        return properties.getQueue().getAcceptPrefix() + executionId;
    }

    /** Accept/status TTL aligned with the longest bounded execution artifact TTL. */
    long acceptMarkerTtlSeconds() {
        ComplexityProfileExecutionProperties.Queue queue = properties.getQueue();
        return Math.max(
                queue.getStatusTtlSeconds(),
                Math.max(queue.getResultTtlSeconds(), queue.getQueuedJobMaxAgeSeconds()));
    }

    private <T> Optional<T> deserialize(Object value, Class<T> type) {
        if (value == null) {
            return Optional.empty();
        }
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        try {
            String json = value instanceof String s ? s : objectMapper.writeValueAsString(value);
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            log.error("[PROFILE-QUEUE] deserialize failure for {}", type.getSimpleName(), e);
            return Optional.empty();
        }
    }
}

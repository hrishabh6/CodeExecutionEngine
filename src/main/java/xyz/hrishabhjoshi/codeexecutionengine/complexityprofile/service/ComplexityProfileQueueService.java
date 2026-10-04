package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.execution.ExecutionRequestRejectedException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexityProfileQueueService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ComplexityProfileExecutionProperties properties;

    public String enqueue(ComplexityProfileJobPayload payload) {
        Long depth = redisTemplate.opsForList().size(properties.getQueue().getName());
        if (depth != null && depth >= properties.getWorker().getQueueCapacity()) {
            throw new ExecutionRequestRejectedException("PROFILE_QUEUE_FULL", "Complexity profile queue is full");
        }
        String executionId = payload.getExecutionId();
        if (executionId == null || executionId.isBlank()) {
            executionId = UUID.randomUUID().toString();
            payload.setExecutionId(executionId);
        }
        payload.setEnqueuedAtMs(System.currentTimeMillis());
        setStatus(executionId, new ComplexityProfilePollResponse(
                executionId, "QUEUED", null, null, null, null, null, null, null, null, null));
        String jobKey = properties.getQueue().getJobPayloadPrefix() + executionId;
        redisTemplate.opsForValue().set(
                jobKey,
                payload,
                properties.getQueue().getQueuedJobMaxAgeSeconds(),
                TimeUnit.SECONDS);
        redisTemplate.opsForList().leftPush(properties.getQueue().getName(), executionId);
        return executionId;
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

    private <T> Optional<T> deserialize(Object value, Class<T> type) {
        if (value == null) {
            return Optional.empty();
        }
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            log.error("[PROFILE-QUEUE] deserialize failure for {}", type.getSimpleName(), e);
            return Optional.empty();
        }
    }
}

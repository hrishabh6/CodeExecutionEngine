package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxMeasurementResult;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ComplexityProfileSandboxRedisStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ComplexityProfileExecutionProperties properties;
    private final ObjectMapper objectMapper;

    public void stageSandboxPayload(ComplexityProfileSandboxJobPayload payload) {
        assertSandboxKey(payloadKey(payload.executionId()));
        redisTemplate.opsForValue().set(
                payloadKey(payload.executionId()),
                payload,
                properties.getQueue().getQueuedJobMaxAgeSeconds(),
                TimeUnit.SECONDS);
    }

    public Optional<ComplexityProfileSandboxJobPayload> loadSandboxPayload(String executionId) {
        return deserialize(redisTemplate.opsForValue().get(payloadKey(executionId)), ComplexityProfileSandboxJobPayload.class);
    }

    public void deleteSandboxPayload(String executionId) {
        redisTemplate.delete(payloadKey(executionId));
    }

    public Optional<ComplexityProfileSandboxMeasurementResult> loadSandboxMeasurement(String executionId) {
        return deserialize(
                redisTemplate.opsForValue().get(measurementKey(executionId)),
                ComplexityProfileSandboxMeasurementResult.class);
    }

    public void deleteSandboxMeasurement(String executionId) {
        redisTemplate.delete(measurementKey(executionId));
    }

    public ComplexityProfileSandboxJobRedisClient forSandboxJob(String host, int port, String username, String password) {
        return new ComplexityProfileSandboxJobRedisClient(host, port, username, password, properties, objectMapper);
    }

    public String payloadKey(String executionId) {
        return properties.getSandbox().getSandboxRedisKeyPrefix() + "payload:" + executionId;
    }

    public String measurementKey(String executionId) {
        return properties.getSandbox().getSandboxRedisKeyPrefix() + "measurement:" + executionId;
    }

    private void assertSandboxKey(String key) {
        if (!key.startsWith(properties.getSandbox().getSandboxRedisKeyPrefix())) {
            throw new IllegalArgumentException("sandbox redis key outside allowed prefix");
        }
    }

    private <T> Optional<T> deserialize(Object raw, Class<T> type) {
        if (raw == null) {
            return Optional.empty();
        }
        if (type.isInstance(raw)) {
            return Optional.of(type.cast(raw));
        }
        try {
            String json = objectMapper.writeValueAsString(raw);
            return Optional.of(objectMapper.readValue(json, type));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}

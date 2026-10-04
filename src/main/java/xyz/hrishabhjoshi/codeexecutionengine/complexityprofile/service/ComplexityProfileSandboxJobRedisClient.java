package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxMeasurementResult;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Restricted Redis client for profile Job pods (ACL user scoped to sandbox keys). */
public final class ComplexityProfileSandboxJobRedisClient implements AutoCloseable {

    private final LettuceConnectionFactory connectionFactory;
    private final RedisTemplate<String, Object> template;
    private final ComplexityProfileExecutionProperties properties;
    private final ObjectMapper objectMapper;

    public ComplexityProfileSandboxJobRedisClient(
            String host,
            int port,
            String username,
            String password,
            ComplexityProfileExecutionProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        if (username != null && !username.isBlank()) {
            config.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            config.setPassword(RedisPassword.of(password));
        }
        this.connectionFactory = new LettuceConnectionFactory(config);
        this.connectionFactory.afterPropertiesSet();
        this.template = new RedisTemplate<>();
        this.template.setConnectionFactory(connectionFactory);
        this.template.setKeySerializer(new StringRedisSerializer());
        this.template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
        this.template.afterPropertiesSet();
    }

    public Optional<ComplexityProfileSandboxJobPayload> loadPayload(String executionId) {
        return deserialize(template.opsForValue().get(payloadKey(executionId)), ComplexityProfileSandboxJobPayload.class);
    }

    public void storeMeasurement(ComplexityProfileSandboxMeasurementResult result) {
        template.opsForValue().set(
                measurementKey(result.executionId()),
                result,
                properties.getQueue().getResultTtlSeconds(),
                TimeUnit.SECONDS);
    }

    public void deletePayload(String executionId) {
        template.delete(payloadKey(executionId));
    }

    private String payloadKey(String executionId) {
        return properties.getSandbox().getSandboxRedisKeyPrefix() + "payload:" + executionId;
    }

    private String measurementKey(String executionId) {
        return properties.getSandbox().getSandboxRedisKeyPrefix() + "measurement:" + executionId;
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

    @Override
    public void close() {
        connectionFactory.destroy();
    }
}

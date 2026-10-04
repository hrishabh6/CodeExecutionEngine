package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileTrustedOracleBundle;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ComplexityProfileTrustedOracleStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ComplexityProfileExecutionProperties properties;

    public void save(ComplexityProfileTrustedOracleBundle bundle) {
        String key = oracleKey(bundle.executionId());
        redisTemplate.opsForValue().set(
                key,
                bundle,
                properties.getQueue().getQueuedJobMaxAgeSeconds(),
                TimeUnit.SECONDS);
    }

    public Optional<ComplexityProfileTrustedOracleBundle> load(String executionId) {
        Object raw = redisTemplate.opsForValue().get(oracleKey(executionId));
        return deserialize(raw);
    }

    public void delete(String executionId) {
        redisTemplate.delete(oracleKey(executionId));
    }

    private String oracleKey(String executionId) {
        return properties.getSandbox().getTrustedOracleKeyPrefix() + executionId;
    }

    private Optional<ComplexityProfileTrustedOracleBundle> deserialize(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        if (raw instanceof ComplexityProfileTrustedOracleBundle bundle) {
            return Optional.of(bundle);
        }
        try {
            String json = objectMapper.writeValueAsString(raw);
            return Optional.of(objectMapper.readValue(json, ComplexityProfileTrustedOracleBundle.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}

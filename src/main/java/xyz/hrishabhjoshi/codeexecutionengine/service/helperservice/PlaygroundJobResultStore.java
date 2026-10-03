package xyz.hrishabhjoshi.codeexecutionengine.service.helperservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.RawExecutionResult;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundJobResultStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${execution.queue.playground-job-result-prefix:execution:playground-job-result:}")
    private String resultPrefix;

    @Value("${execution.queue.job-result-ttl-seconds:600}")
    private long resultTtlSeconds;

    public void save(String executionId, RawExecutionResult result) {
        redisTemplate.opsForValue().set(key(executionId), result, resultTtlSeconds, TimeUnit.SECONDS);
        log.info("[PLAYGROUND_JOB_RESULT] Saved executionId={}", executionId);
    }

    public Optional<RawExecutionResult> get(String executionId) {
        Object value = redisTemplate.opsForValue().get(key(executionId));
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof RawExecutionResult raw) {
            return Optional.of(raw);
        }
        try {
            String json = objectMapper.writeValueAsString(value);
            return Optional.of(objectMapper.readValue(json, RawExecutionResult.class));
        } catch (JsonProcessingException e) {
            log.error("[PLAYGROUND_JOB_RESULT] deserialize failed executionId={}: {}", executionId, e.getMessage());
            return Optional.empty();
        }
    }

    public void delete(String executionId) {
        redisTemplate.delete(key(executionId));
    }

    private String key(String executionId) {
        return resultPrefix + executionId;
    }
}

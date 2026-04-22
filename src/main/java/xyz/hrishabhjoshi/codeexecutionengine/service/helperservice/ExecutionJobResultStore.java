package xyz.hrishabhjoshi.codeexecutionengine.service.helperservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionJobResultStore {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${execution.queue.job-result-prefix:execution:job-result:}")
    private String jobResultPrefix;

    @Value("${execution.queue.job-result-ttl-seconds:600}")
    private long resultTtlSeconds;

    public void save(String executionId, CodeExecutionResultDTO result) {
        String key = key(executionId);
        redisTemplate.opsForValue().set(key, result, resultTtlSeconds, TimeUnit.SECONDS);
        log.info("[JOB_RESULT] Saved result for executionId={} at key={}", executionId, key);
    }

    public Optional<CodeExecutionResultDTO> get(String executionId) {
        Object result = redisTemplate.opsForValue().get(key(executionId));
        if (result == null) {
            return Optional.empty();
        }
        if (result instanceof CodeExecutionResultDTO dto) {
            return Optional.of(dto);
        }

        try {
            String json = objectMapper.writeValueAsString(result);
            return Optional.of(objectMapper.readValue(json, CodeExecutionResultDTO.class));
        } catch (JsonProcessingException e) {
            log.error("[JOB_RESULT] Failed to deserialize result for executionId={}: {}", executionId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Optional<CodeExecutionResultDTO> await(String executionId, Duration timeout, Duration pollInterval) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Optional<CodeExecutionResultDTO> result = get(executionId);
            if (result.isPresent()) {
                return result;
            }

            try {
                Thread.sleep(Math.max(100, pollInterval.toMillis()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public void delete(String executionId) {
        redisTemplate.delete(key(executionId));
    }

    private String key(String executionId) {
        return jobResultPrefix + executionId;
    }
}

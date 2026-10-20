package xyz.hrishabhjoshi.codeexecutionengine.service.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionV2Properties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "execution.v2", name = "enabled", havingValue = "true")
public class ExecutionV2StatusStore {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutionV2Properties properties;

    public Optional<SubmissionStatusDto> getStatus(String submissionId) {
        String json = stringRedisTemplate.opsForValue().get(properties.toKeys().statusKey(submissionId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SubmissionStatusDto.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize v2 status for submission {}: {}", submissionId, e.getMessage());
            return Optional.empty();
        }
    }
}

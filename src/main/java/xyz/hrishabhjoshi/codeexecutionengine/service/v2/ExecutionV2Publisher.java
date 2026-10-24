package xyz.hrishabhjoshi.codeexecutionengine.service.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionV2Properties;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.ExecutionContractVersion;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.PlaygroundTaskPayload;
import xyz.hrishabhjoshi.codeexecutionengine.core.mapping.ExecutionRequestMapper;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.ExecutionV2Keys;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.LuaScriptLoader;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionRequest;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;
import xyz.hrishabhjoshi.codeexecutionengine.execution.ExecutionRequestRejectedException;
import xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice.ExecutionPayloadCodec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "execution.v2", name = "enabled", havingValue = "true")
public class ExecutionV2Publisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ExecutionV2Properties v2Properties;
    private final ExecutionPayloadCodec payloadCodec;

    private DefaultRedisScript<List> enqueueScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(LuaScriptLoader.enqueueScript());
        script.setResultType(List.class);
        return script;
    }

    public String publish(ExecutionRequest request) {
        ensureIdentifiers(request);
        ExecutionV2Keys keys = v2Properties.toKeys();
        try {
            String payloadBase64 = playground(request)
                    ? Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(new PlaygroundTaskPayload(
                            request.getLanguage(),
                            request.getCode(),
                            request.getStdin())))
                    : payloadCodec.encode(ExecutionRequestMapper.toSubmission(request));
            RunnerTaskEnvelope envelope = new RunnerTaskEnvelope(
                    ExecutionContractVersion.V2_RUNNER_QUEUE,
                    request.getExecutionId(),
                    "0",
                    request.getSubmissionId(),
                    request.getExecutionId(),
                    request.getExecutionMode(),
                    request.getLanguage(),
                    payloadBase64,
                    Instant.now().plusSeconds(v2Properties.getTaskTtlSeconds()),
                    "default");
            String taskJson = objectMapper.writeValueAsString(envelope);
            SubmissionStatusDto status = SubmissionStatusDto.builder()
                    .submissionId(request.getSubmissionId())
                    .executionId(request.getExecutionId())
                    .status("QUEUED")
                    .queuedAt(System.currentTimeMillis())
                    .build();
            String statusJson = objectMapper.writeValueAsString(status);
            String contentHash = sha256(taskJson);

            List<?> result = stringRedisTemplate.execute(
                    enqueueScript(),
                    List.of(
                            keys.readyList(),
                            keys.processingList(),
                            keys.taskKey(request.getExecutionId()),
                            keys.statusKey(request.getSubmissionId()),
                            keys.queuedZset()),
                    request.getExecutionId(),
                    taskJson,
                    statusJson,
                    contentHash,
                    String.valueOf(v2Properties.getQueueCapacity()),
                    String.valueOf(v2Properties.getTaskTtlSeconds()),
                    String.valueOf(v2Properties.getStatusTtlSeconds()),
                    String.valueOf(System.currentTimeMillis()),
                    contentHash);

            if (result == null || result.isEmpty()) {
                throw new ExecutionRequestRejectedException("V2_ENQUEUE_FAILED", "Enqueue returned empty");
            }
            long code = Long.parseLong(String.valueOf(result.get(0)));
            String label = String.valueOf(result.get(1));
            if (code == 0 && "QUEUE_FULL".equals(label)) {
                throw new ExecutionRequestRejectedException("EXECUTION_QUEUE_FULL", "Execution queue is full");
            }
            if (code == 0 && "CONFLICT".equals(label)) {
                throw new ExecutionRequestRejectedException("EXECUTION_ID_CONFLICT", "Execution ID conflict");
            }
            log.info("[V2] enqueue executionId={} result={}", request.getExecutionId(), label);
            return request.getSubmissionId();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize v2 task", e);
        }
    }

    private static boolean playground(ExecutionRequest request) {
        return request.getExecutionMode() != null
                && "PLAYGROUND".equalsIgnoreCase(request.getExecutionMode());
    }

    static void ensureIdentifiers(ExecutionRequest request) {
        if (request.getSubmissionId() == null || request.getSubmissionId().isBlank()) {
            request.setSubmissionId(UUID.randomUUID().toString());
        }
        if (request.getExecutionId() == null || request.getExecutionId().isBlank()) {
            request.setExecutionId(UUID.randomUUID().toString());
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

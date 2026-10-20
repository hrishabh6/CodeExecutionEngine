package xyz.hrishabhjoshi.codeexecutionengine.core.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class ExecutionPayloadCodecCore {

    private final ObjectMapper objectMapper;

    public ExecutionPayloadCodecCore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(CodeSubmissionDTO submission) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(submission);
            return Base64.getEncoder().encodeToString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize execution payload", e);
        }
    }

    public CodeSubmissionDTO decode(String payloadBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(payloadBase64.getBytes(StandardCharsets.UTF_8));
            return objectMapper.readValue(decoded, CodeSubmissionDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize execution payload", e);
        }
    }
}

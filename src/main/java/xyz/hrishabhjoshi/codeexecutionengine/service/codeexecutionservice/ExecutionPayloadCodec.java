package xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.core.codec.ExecutionPayloadCodecCore;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;

@Component
@RequiredArgsConstructor
public class ExecutionPayloadCodec {

    private final ObjectMapper objectMapper;

    private ExecutionPayloadCodecCore core() {
        return new ExecutionPayloadCodecCore(objectMapper);
    }

    public String encode(CodeSubmissionDTO submission) {
        return core().encode(submission);
    }

    public CodeSubmissionDTO decode(String payloadBase64) {
        return core().decode(payloadBase64);
    }
}

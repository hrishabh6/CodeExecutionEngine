package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfileSubmitRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfileSubmitResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComplexityProfileOrchestrationService {

    private final ComplexityProfileRequestValidator requestValidator;
    private final ComplexityProfileQueueService queueService;
    private final ComplexityProfileExecutionProperties properties;

    public ComplexityProfileSubmitResponse submit(ComplexityProfileSubmitRequest request) {
        requestValidator.validate(request);
        String executionId = UUID.randomUUID().toString();
        ComplexityProfileJobPayload payload = ComplexityProfileJobPayload.builder()
                .executionId(executionId)
                .submissionId(request.submissionId())
                .questionId(request.questionId())
                .language(request.language())
                .sourceCode(request.sourceCode())
                .questionMetadata(request.questionMetadata())
                .profileCode(request.profileCode())
                .profileVersion(request.profileVersion())
                .profileHash(request.profileHash())
                .harnessVersion(request.harnessVersion())
                .cases(request.cases())
                .build();
        queueService.enqueue(payload);
        return new ComplexityProfileSubmitResponse(executionId, "QUEUED");
    }

    public Optional<ComplexityProfilePollResponse> poll(String executionId) {
        Optional<ComplexityProfilePollResponse> terminal = queueService.getResult(executionId);
        if (terminal.isPresent()) {
            return terminal;
        }
        return queueService.getStatus(executionId);
    }
}

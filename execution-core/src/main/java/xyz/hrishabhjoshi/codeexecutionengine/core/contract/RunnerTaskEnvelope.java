package xyz.hrishabhjoshi.codeexecutionengine.core.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunnerTaskEnvelope(
        String contractVersion,
        String taskId,
        String attemptId,
        String submissionId,
        String executionId,
        String mode,
        String language,
        String payloadBase64,
        Instant expiresAt,
        String limitsProfileId) {

    public RunnerTaskEnvelope {
        if (contractVersion == null || contractVersion.isBlank()) {
            contractVersion = ExecutionContractVersion.V2_RUNNER_QUEUE;
        }
    }
}

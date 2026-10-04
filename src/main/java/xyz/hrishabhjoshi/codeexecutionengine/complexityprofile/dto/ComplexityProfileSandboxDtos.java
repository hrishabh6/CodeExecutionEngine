package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto;

import com.fasterxml.jackson.databind.JsonNode;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;

import java.util.List;
import java.util.Map;

/**
 * Production Kubernetes sandbox contract — no trusted expected outputs in the Job payload.
 */
public final class ComplexityProfileSandboxDtos {

    private ComplexityProfileSandboxDtos() {
    }

    public record ComplexityProfileSandboxJobPayload(
            String executionId,
            String submissionId,
            Long questionId,
            String language,
            String sourceCode,
            QuestionMetadataDto questionMetadata,
            String profileCode,
            String profileVersion,
            String profileHash,
            String harnessVersion,
            List<SandboxProfileCaseRequest> cases) {
    }

    public record SandboxProfileCaseRequest(
            String caseId,
            String caseIdentity,
            String profileCode,
            String profileVersion,
            String profileHash,
            String generatorVersion,
            String variant,
            Map<String, Integer> sizeVector,
            String seed,
            JsonNode input,
            String inputHash,
            int warmups,
            int measuredRepeats) {
    }

    public record ComplexityProfileTrustedOracleBundle(
            String executionId,
            QuestionMetadataDto questionMetadata,
            List<TrustedOracleCaseExpectation> cases) {
    }

    public record TrustedOracleCaseExpectation(String caseId, JsonNode expectedOutput) {
    }

    public record ComplexityProfileSandboxMeasurementResult(
            String executionId,
            String compileStatus,
            String errorMessage,
            List<SandboxCaseMeasurement> cases) {
    }

    public record SandboxCaseMeasurement(
            String caseId,
            String caseIdentity,
            String profileCode,
            String profileVersion,
            String profileHash,
            String generatorVersion,
            String variant,
            Map<String, Integer> sizeVector,
            String outcome,
            JsonNode actualOutput,
            int warmupCount,
            int sampleCount,
            Long medianElapsedNs,
            Long madElapsedNs,
            Long minElapsedNs,
            Long maxElapsedNs,
            String errorCode) {
    }
}

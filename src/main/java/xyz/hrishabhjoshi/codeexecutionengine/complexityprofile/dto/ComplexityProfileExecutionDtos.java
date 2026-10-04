package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public final class ComplexityProfileExecutionDtos {

    private ComplexityProfileExecutionDtos() {
    }

    public record ComplexityProfileSubmitRequest(
            @NotBlank String submissionId,
            @NotNull Long questionId,
            @NotBlank String language,
            @NotBlank String sourceCode,
            @NotNull @Valid QuestionMetadataDto questionMetadata,
            @NotBlank String profileCode,
            @NotBlank String profileVersion,
            @NotBlank String profileHash,
            @NotBlank String harnessVersion,
            @NotEmpty @Valid List<ProfileCaseRequest> cases) {
    }

    public record QuestionMetadataDto(
            @NotBlank String fullyQualifiedPackageName,
            @NotBlank String functionName,
            @NotBlank String returnType,
            @NotEmpty List<ParameterDto> parameters,
            Map<String, String> customDataStructures,
            String questionType,
            Boolean isOutputOrderMatters) {
    }

    public record ParameterDto(@NotBlank String name, @NotBlank String type) {
    }

    public record ProfileCaseRequest(
            @NotBlank String caseId,
            @NotBlank String caseIdentity,
            @NotBlank String profileCode,
            @NotBlank String profileVersion,
            @NotBlank String profileHash,
            @NotBlank String generatorVersion,
            String variant,
            Map<String, Integer> sizeVector,
            String seed,
            @NotNull JsonNode input,
            @NotBlank String inputHash,
            @NotNull JsonNode expectedOutput,
            int warmups,
            int measuredRepeats) {
    }

    public record ComplexityProfileSubmitResponse(
            @NotBlank String executionId,
            @NotBlank String status) {
    }

    public record ComplexityProfilePollResponse(
            @NotBlank String executionId,
            @NotBlank String status,
            String compileStatus,
            String jdkVersion,
            String harnessVersion,
            String measurementPolicyVersion,
            String profilerRuntimeVersion,
            String environmentFingerprint,
            List<ProfileCaseResult> cases,
            String errorCode,
            String errorMessage) {
    }

    public record ProfileCaseResult(
            String caseId,
            String caseIdentity,
            String profileCode,
            String profileVersion,
            String profileHash,
            String generatorVersion,
            String variant,
            Map<String, Integer> sizeVector,
            String outcome,
            boolean outputValidated,
            int warmupCount,
            int sampleCount,
            Long medianElapsedNs,
            Long madElapsedNs,
            Long minElapsedNs,
            Long maxElapsedNs,
            String errorCode) {
    }
}

package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfileSubmitRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseRequest;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.execution.ExecutionRequestRejectedException;

import java.nio.charset.StandardCharsets;

@Component
public class ComplexityProfileRequestValidator {

    private final ComplexityProfileExecutionProperties properties;

    public ComplexityProfileRequestValidator(ComplexityProfileExecutionProperties properties) {
        this.properties = properties;
    }

    public void validate(ComplexityProfileSubmitRequest request) {
        if (!properties.isEnabled()) {
            throw new ExecutionRequestRejectedException(
                    "EXECUTION_MODE_DISABLED",
                    "COMPLEXITY_PROFILE execution is not enabled");
        }
        if ("kubernetes-job".equalsIgnoreCase(properties.getSandbox().getBackend())) {
            throw new ExecutionRequestRejectedException(
                    "SANDBOX_NOT_READY",
                    "Production complexity profile sandbox is not available in Phase 4");
        }
        if (!"JAVA".equalsIgnoreCase(request.language().trim())) {
            throw new ExecutionRequestRejectedException("INVALID_LANGUAGE", "Only JAVA is supported for profiling");
        }
        enforceUtf8Limit(request.sourceCode(), properties.getLimits().getMaxSourceBytes(), "SOURCE_TOO_LARGE");
        if (request.cases().size() > properties.getLimits().getMaxCases()) {
            throw new ExecutionRequestRejectedException("TOO_MANY_CASES", "Case count exceeds limit");
        }
        if (!properties.getHarnessVersion().equals(request.harnessVersion())) {
            throw new ExecutionRequestRejectedException(
                    "HARNESS_VERSION_MISMATCH",
                    "Unsupported harnessVersion");
        }
        for (ProfileCaseRequest profileCase : request.cases()) {
            validateCase(profileCase, request);
        }
    }

    private void validateCase(ProfileCaseRequest profileCase, ComplexityProfileSubmitRequest request) {
        if (profileCase.warmups() > properties.getLimits().getMaxWarmupsPerCase()
                || profileCase.measuredRepeats() > properties.getLimits().getMaxMeasuredRepeatsPerCase()
                || profileCase.measuredRepeats() <= 0) {
            throw new ExecutionRequestRejectedException("INVALID_SAMPLE_PLAN", "Warmup/measured repeat limits exceeded");
        }
        if (!request.profileVersion().equals(profileCase.profileVersion())
                || !request.profileHash().equals(profileCase.profileHash())
                || !request.profileCode().equals(profileCase.profileCode())) {
            throw new ExecutionRequestRejectedException("PROFILE_CORRELATION_MISMATCH", "Case profile metadata mismatch");
        }
        enforceJsonLimit(profileCase.input(), properties.getLimits().getMaxCaseInputBytes(), "CASE_INPUT_TOO_LARGE");
        enforceJsonLimit(profileCase.expectedOutput(), properties.getLimits().getMaxExpectedOutputBytes(), "EXPECTED_OUTPUT_TOO_LARGE");
    }

    private static void enforceUtf8Limit(String value, int maxBytes, String code) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new ExecutionRequestRejectedException(code, "Payload exceeds size limit");
        }
    }

    private static void enforceJsonLimit(JsonNode node, int maxBytes, String code) {
        enforceUtf8Limit(node.toString(), maxBytes, code);
    }
}

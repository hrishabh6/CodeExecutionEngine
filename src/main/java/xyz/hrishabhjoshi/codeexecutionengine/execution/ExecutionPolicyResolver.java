package xyz.hrishabhjoshi.codeexecutionengine.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.config.PlaygroundExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.PlaygroundSandboxProperties;
import xyz.hrishabhjoshi.codeexecutionengine.service.execution.PlaygroundKubernetesJobService;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionMode;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionRequest;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Normalizes {@code executionMode} and applies trusted ingress/worker policy per mode.
 */
@Component
@RequiredArgsConstructor
public class ExecutionPolicyResolver {

    private final PlaygroundExecutionProperties playgroundProperties;
    private final PlaygroundSandboxProperties sandboxProperties;
    private final PlaygroundKubernetesJobService playgroundKubernetesJobService;

    public ExecutionMode resolveMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return ExecutionMode.SUBMISSION;
        }
        try {
            return ExecutionMode.valueOf(rawMode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ExecutionRequestRejectedException(
                    "INVALID_EXECUTION_MODE",
                    "Unknown executionMode: " + rawMode);
        }
    }

    public void validateIngress(ExecutionMode mode, ExecutionRequest request) {
        switch (mode) {
            case COMPLEXITY_PROFILE ->
                    throw new ExecutionRequestRejectedException(
                            "EXECUTION_MODE_DISABLED",
                            "COMPLEXITY_PROFILE is not enabled");
            case PLAYGROUND -> validatePlaygroundIngress(request);
            case SUBMISSION -> validateSubmissionIngress(request);
        }
    }

    public boolean isWorkerRunnable(ExecutionMode mode) {
        return switch (mode) {
            case SUBMISSION -> true;
            case PLAYGROUND -> playgroundProperties.isEnabled();
            case COMPLEXITY_PROFILE -> false;
        };
    }

    private void validateSubmissionIngress(ExecutionRequest request) {
        if (request.getStdin() != null && !request.getStdin().isEmpty()) {
            throw new ExecutionRequestRejectedException(
                    "INVALID_SUBMISSION_REQUEST",
                    "stdin is not allowed for SUBMISSION executionMode");
        }
    }

    private void validatePlaygroundIngress(ExecutionRequest request) {
        if (!playgroundProperties.isEnabled()) {
            throw new ExecutionRequestRejectedException(
                    "EXECUTION_MODE_DISABLED",
                    "PLAYGROUND is not enabled");
        }
        if (sandboxProperties.isKubernetesJobBackend() && !playgroundKubernetesJobService.isConfigured()) {
            throw new ExecutionRequestRejectedException(
                    "SANDBOX_NOT_READY",
                    "Playground kubernetes-job sandbox is not configured");
        }
        if (request.getMetadata() != null) {
            throw new ExecutionRequestRejectedException(
                    "INVALID_PLAYGROUND_REQUEST",
                    "metadata is not allowed for PLAYGROUND");
        }
        if (request.getTestCases() != null && !request.getTestCases().isEmpty()) {
            throw new ExecutionRequestRejectedException(
                    "INVALID_PLAYGROUND_REQUEST",
                    "testCases are not allowed for PLAYGROUND");
        }
        if (request.getQuestionId() != null) {
            throw new ExecutionRequestRejectedException(
                    "INVALID_PLAYGROUND_REQUEST",
                    "questionId is not allowed for PLAYGROUND");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new ExecutionRequestRejectedException("INVALID_SOURCE", "code is required");
        }
        if (request.getLanguage() == null || request.getLanguage().isBlank()) {
            throw new ExecutionRequestRejectedException("INVALID_LANGUAGE", "language is required");
        }
        String language = request.getLanguage().trim().toUpperCase(Locale.ROOT);
        if (!language.equals("JAVA") && !language.equals("PYTHON")) {
            throw new ExecutionRequestRejectedException("INVALID_LANGUAGE", "Unsupported language");
        }
        enforceUtf8Limit(request.getCode(), playgroundProperties.getMaxSourceBytes(), "SOURCE_TOO_LARGE");
        enforceUtf8Limit(
                request.getStdin() == null ? "" : request.getStdin(),
                playgroundProperties.getMaxStdinBytes(),
                "INPUT_TOO_LARGE");
    }

    private void enforceUtf8Limit(String value, int maxBytes, String code) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new ExecutionRequestRejectedException(code, "Payload exceeds size limit");
        }
    }
}

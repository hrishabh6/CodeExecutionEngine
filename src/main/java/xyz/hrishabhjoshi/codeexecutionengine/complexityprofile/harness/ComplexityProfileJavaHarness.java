package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseResult;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ComplexityProfileJavaHarness {

    private final ManagedProcessRunner processRunner;
    private final ExecutionRuntimeProperties runtimeProperties;
    private final ComplexityProfileExecutionProperties profileProperties;
    private final ObjectMapper objectMapper;
    private final ComplexityProfileChildJvmRunner childJvmRunner;

    @Getter
    private volatile int compileInvocationCount;

    public HarnessRunResult execute(ComplexityProfileJobPayload job) {
        if (profileProperties.getHarness().isUseChildJvm()) {
            HarnessRunResult result = childJvmRunner.execute(job);
            compileInvocationCount = 1;
            return result;
        }
        ComplexityProfileMeasurementEngine engine = new ComplexityProfileMeasurementEngine(
                processRunner, runtimeProperties, profileProperties, objectMapper);
        HarnessRunResult result = engine.execute(job);
        compileInvocationCount = engine.getCompileInvocationCount();
        return result;
    }

    public record HarnessRunResult(String compileStatus, String errorMessage, List<ProfileCaseResult> cases) {
        static HarnessRunResult compileFailed(String message) {
            return new HarnessRunResult("FAILED", message, List.of());
        }

        static HarnessRunResult internalFailed(String message) {
            return new HarnessRunResult("FAILED", message, List.of());
        }

        static HarnessRunResult childTimedOut() {
            return new HarnessRunResult("FAILED", "PROFILE_CHILD_TIMEOUT", List.of());
        }

        static HarnessRunResult completed(List<ProfileCaseResult> cases) {
            return new HarnessRunResult("SUCCESS", null, cases);
        }
    }
}

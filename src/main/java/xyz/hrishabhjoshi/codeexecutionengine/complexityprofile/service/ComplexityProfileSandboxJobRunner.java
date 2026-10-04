package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxMeasurementResult;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness.ComplexityProfileMeasurementEngine;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

@Slf4j
@Component
public class ComplexityProfileSandboxJobRunner {

    private final ComplexityProfileSandboxRedisStore sandboxRedisStore;
    private final ComplexityProfileExecutionProperties properties;
    private final ObjectMapper objectMapper;

    public ComplexityProfileSandboxJobRunner(
            ComplexityProfileSandboxRedisStore sandboxRedisStore,
            ComplexityProfileExecutionProperties properties,
            ObjectMapper objectMapper) {
        this.sandboxRedisStore = sandboxRedisStore;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public int runOneShot(String executionId) {
        String host = requiredEnv("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_HOST");
        int port = Integer.parseInt(requiredEnv("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_PORT"));
        String username = System.getenv("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_USERNAME");
        String password = System.getenv("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_PASSWORD");

        ExecutionRuntimeProperties runtimeProperties = new ExecutionRuntimeProperties();
        runtimeProperties.getLanguages().put("java", defaultJavaRuntime());
        ComplexityProfileMeasurementEngine engine = new ComplexityProfileMeasurementEngine(
                new ManagedProcessRunner(), runtimeProperties, properties, objectMapper);

        try (ComplexityProfileSandboxJobRedisClient client =
                sandboxRedisStore.forSandboxJob(host, port, username, password)) {
            ComplexityProfileSandboxJobPayload payload = client.loadPayload(executionId)
                    .orElseThrow(() -> new IllegalStateException("Sandbox payload missing for executionId=" + executionId));
            log.info("[PROFILE-SANDBOX-JOB] executionId={} cases={}", executionId, payload.cases().size());
            ComplexityProfileSandboxMeasurementResult measurement = engine.executeSandbox(payload);
            client.storeMeasurement(measurement);
            client.deletePayload(executionId);
            return measurement.compileStatus().equals("SUCCESS") ? 0 : 1;
        } catch (Exception e) {
            log.error("[PROFILE-SANDBOX-JOB] executionId={} failed: {}", executionId, e.getMessage());
            return 1;
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for sandbox profile jobs");
        }
        return value;
    }

    private static ExecutionRuntimeProperties.LanguageRuntime defaultJavaRuntime() {
        ExecutionRuntimeProperties.LanguageRuntime runtime = new ExecutionRuntimeProperties.LanguageRuntime();
        runtime.setCompile("javac");
        runtime.setRun("java");
        return runtime;
    }
}

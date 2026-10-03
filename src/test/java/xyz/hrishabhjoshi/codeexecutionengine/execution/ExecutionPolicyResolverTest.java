package xyz.hrishabhjoshi.codeexecutionengine.execution;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.config.PlaygroundExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.PlaygroundSandboxProperties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionMode;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionRequest;
import xyz.hrishabhjoshi.codeexecutionengine.service.execution.PlaygroundKubernetesJobService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutionPolicyResolverTest {

    private PlaygroundExecutionProperties properties;
    private PlaygroundSandboxProperties sandboxProperties;
    private PlaygroundKubernetesJobService kubernetesJobService;
    private ExecutionPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new PlaygroundExecutionProperties();
        sandboxProperties = new PlaygroundSandboxProperties();
        kubernetesJobService = mock(PlaygroundKubernetesJobService.class);
        when(kubernetesJobService.isConfigured()).thenReturn(true);
        resolver = new ExecutionPolicyResolver(properties, sandboxProperties, kubernetesJobService);
    }

    @Test
    void missingModeDefaultsToSubmission() {
        assertEquals(ExecutionMode.SUBMISSION, resolver.resolveMode(null));
        assertEquals(ExecutionMode.SUBMISSION, resolver.resolveMode("  "));
    }

    @Test
    void explicitSubmissionAllowed() {
        ExecutionRequest request = ExecutionRequest.builder().build();
        resolver.validateIngress(ExecutionMode.SUBMISSION, request);
    }

    @Test
    void submissionRejectsStdin() {
        ExecutionRequest request = ExecutionRequest.builder().stdin("1\n").build();
        ExecutionRequestRejectedException ex = assertThrows(
                ExecutionRequestRejectedException.class,
                () -> resolver.validateIngress(ExecutionMode.SUBMISSION, request));
        assertEquals("INVALID_SUBMISSION_REQUEST", ex.getCode());
    }

    @Test
    void playgroundRejectedWhenDisabled() {
        properties.setEnabled(false);
        ExecutionRequest request = validPlaygroundRequest();
        assertThrows(ExecutionRequestRejectedException.class,
                () -> resolver.validateIngress(ExecutionMode.PLAYGROUND, request));
    }

    @Test
    void playgroundAllowedWhenEnabledLocalProcess() {
        properties.setEnabled(true);
        sandboxProperties.setBackend("local-process");
        ExecutionRequest request = validPlaygroundRequest();
        resolver.validateIngress(ExecutionMode.PLAYGROUND, request);
        assertTrue(resolver.isWorkerRunnable(ExecutionMode.PLAYGROUND));
    }

    @Test
    void playgroundKubernetesRequiresConfiguredSandbox() {
        properties.setEnabled(true);
        sandboxProperties.setBackend("kubernetes-job");
        sandboxProperties.setProductionVerified(true);
        when(kubernetesJobService.isConfigured()).thenReturn(false);
        ExecutionRequest request = validPlaygroundRequest();
        assertThrows(ExecutionRequestRejectedException.class,
                () -> resolver.validateIngress(ExecutionMode.PLAYGROUND, request));
    }

    @Test
    void complexityRejectedAtIngress() {
        ExecutionRequest request = ExecutionRequest.builder().build();
        assertThrows(ExecutionRequestRejectedException.class,
                () -> resolver.validateIngress(ExecutionMode.COMPLEXITY_PROFILE, request));
    }

    @Test
    void unknownModeRejected() {
        assertThrows(ExecutionRequestRejectedException.class, () -> resolver.resolveMode("BENCHMARK"));
    }

    private ExecutionRequest validPlaygroundRequest() {
        return ExecutionRequest.builder()
                .language("JAVA")
                .code("public class Main { public static void main(String[] args) {} }")
                .stdin("")
                .build();
    }
}

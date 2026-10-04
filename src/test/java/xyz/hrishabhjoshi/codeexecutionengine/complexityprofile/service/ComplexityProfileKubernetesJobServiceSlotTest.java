package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.KubernetesExecutionProperties;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplexityProfileKubernetesJobServiceSlotTest {

    @Mock
    private io.fabric8.kubernetes.client.KubernetesClient kubernetesClient;
    @Mock
    private KubernetesExecutionProperties kubernetesProperties;
    @Mock
    private ComplexityProfileExecutionProperties profileProperties;
    @Mock
    private ComplexityProfileQueueService queueService;
    @Mock
    private ComplexityProfileActiveJobGuard activeJobGuard;
    @Mock
    private ComplexityProfileJobSlotRegistry slotRegistry;
    @Mock
    private ComplexityProfileSandboxPayloadFactory sandboxPayloadFactory;
    @Mock
    private ComplexityProfileSandboxRedisStore sandboxRedisStore;
    @Mock
    private ComplexityProfileTrustedOracleStore oracleStore;
    @Mock
    private ComplexityProfileTrustedResultAssembler resultAssembler;

    @InjectMocks
    private ComplexityProfileKubernetesJobService service;

    @Test
    void releasesAcquiredSlotWhenActiveJobGuardDenies() {
        ComplexityProfileExecutionProperties.Sandbox sandbox = new ComplexityProfileExecutionProperties.Sandbox();
        sandbox.setSandboxRedisUsername("u");
        sandbox.setSandboxRedisPassword("p");
        sandbox.setRequireSandboxRedisCredentials(true);
        when(profileProperties.getSandbox()).thenReturn(sandbox);
        when(kubernetesProperties.getJobImage()).thenReturn("img:1");
        when(slotRegistry.tryAcquire("exec-1")).thenReturn(Optional.of(0));
        when(activeJobGuard.hasCapacity()).thenReturn(false);

        service.runIsolated(ComplexityProfileJobPayload.builder().executionId("exec-1").submissionId("s").build());

        verify(slotRegistry).release("exec-1", Optional.of(0));
    }
}

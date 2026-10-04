package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.KubernetesExecutionProperties;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SandboxedKubernetesJobBuilderProfileTest {

    @Test
    void profileJobHasHardenedSecurityResourcesAndNoPayloadEnv() {
        KubernetesExecutionProperties k8s = new KubernetesExecutionProperties();
        k8s.setJobImage("example/cxe:2.0.0");

        ComplexityProfileExecutionProperties profile = new ComplexityProfileExecutionProperties();
        profile.getSandbox().setJobServiceAccountName("cxe-complexity-profile-executor");
        profile.getSandbox().setJobActiveDeadlineSeconds(180L);
        profile.getSandbox().setTtlSecondsAfterFinished(300);
        profile.getSandbox().getJobResources().setMemoryLimit("768Mi");

        Job job = SandboxedKubernetesJobBuilder.buildProfileJob(
                "cp-test",
                "algocrack",
                "sub-1",
                "exec-abc",
                Map.of(
                        "EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_HOST", "redis",
                        "EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_PORT", "6379"),
                k8s,
                profile);

        assertEquals("complexity-profile-job", job.getMetadata().getLabels().get("component"));
        assertEquals(0, job.getSpec().getBackoffLimit());
        assertEquals(180L, job.getSpec().getActiveDeadlineSeconds());
        assertEquals(300, job.getSpec().getTtlSecondsAfterFinished());

        var podSpec = job.getSpec().getTemplate().getSpec();
        assertFalse(Boolean.TRUE.equals(podSpec.getAutomountServiceAccountToken()));
        assertEquals("cxe-complexity-profile-executor", podSpec.getServiceAccountName());

        var container = podSpec.getContainers().getFirst();
        assertTrue(container.getSecurityContext().getReadOnlyRootFilesystem());
        assertEquals("RuntimeDefault", container.getSecurityContext().getSeccompProfile().getType());
        assertNull(container.getEnv().stream()
                .filter(e -> "EXECUTION_JOB_PAYLOAD_B64".equals(e.getName()))
                .findFirst()
                .orElse(null));
        assertEquals("COMPLEXITY_PROFILE", container.getEnv().stream()
                .filter(e -> "EXECUTION_JOB_KIND".equals(e.getName()))
                .findFirst()
                .orElseThrow()
                .getValue());
        assertEquals("exec-abc", container.getEnv().stream()
                .filter(e -> "COMPLEXITY_PROFILE_EXECUTION_ID".equals(e.getName()))
                .findFirst()
                .orElseThrow()
                .getValue());
        assertTrue(container.getEnv().stream()
                .anyMatch(e -> "EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_HOST".equals(e.getName())));
        var passwordEnv = container.getEnv().stream()
                .filter(e -> "EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_PASSWORD".equals(e.getName()))
                .findFirst()
                .orElseThrow();
        assertNotNull(passwordEnv.getValueFrom());
        assertNotNull(passwordEnv.getValueFrom().getSecretKeyRef());
        assertEquals("cxe-complexity-profile-sandbox-redis", passwordEnv.getValueFrom().getSecretKeyRef().getName());
        assertEquals("password", passwordEnv.getValueFrom().getSecretKeyRef().getKey());
        assertNull(passwordEnv.getValue());
        assertFalse(container.getEnv().stream()
                .anyMatch(e -> "SPRING_DATA_REDIS_PASSWORD".equals(e.getName())));
        assertNotNull(container.getResources().getLimits().get("memory"));
    }
}

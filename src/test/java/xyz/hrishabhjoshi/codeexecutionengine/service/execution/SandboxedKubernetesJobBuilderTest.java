package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.config.KubernetesExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.PlaygroundSandboxProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.RunnerClasspath;

import static org.junit.jupiter.api.Assertions.*;

class SandboxedKubernetesJobBuilderTest {

    @Test
    void playgroundJobHasHardenedPodAndContainerSecurity() {
        KubernetesExecutionProperties k8s = new KubernetesExecutionProperties();
        k8s.setJobImage("example/cxe:latest");
        k8s.getResources().getLimits().setMemory("256Mi");

        PlaygroundSandboxProperties sandbox = new PlaygroundSandboxProperties();
        sandbox.setJobServiceAccountName("cxe-playground-executor");

        Job job = SandboxedKubernetesJobBuilder.buildJob(
                "pg-test",
                "algocrack",
                "sub-1",
                "playground-abc",
                "payload",
                null,
                k8s,
                sandbox,
                true);

        var podSpec = job.getSpec().getTemplate().getSpec();
        assertFalse(Boolean.TRUE.equals(podSpec.getAutomountServiceAccountToken()));
        assertEquals("cxe-playground-executor", podSpec.getServiceAccountName());
        assertEquals("playground-execution-job", job.getMetadata().getLabels().get("component"));

        var container = podSpec.getContainers().getFirst();
        assertTrue(container.getSecurityContext().getReadOnlyRootFilesystem());
        assertTrue(container.getSecurityContext().getRunAsNonRoot());
        assertEquals(65532L, container.getSecurityContext().getRunAsUser());
        assertFalse(container.getSecurityContext().getAllowPrivilegeEscalation());
        assertNotNull(container.getVolumeMounts());
        assertTrue(container.getVolumeMounts().stream().anyMatch(m -> "/tmp".equals(m.getMountPath())));
        assertTrue(container.getEnv().stream().anyMatch(env ->
                RunnerClasspath.ENV_NAME.equals(env.getName())
                        && RunnerClasspath.IMAGE_LIBS_GLOB.equals(env.getValue())));
        assertTrue(container.getEnv().stream().map(EnvVar::getName).noneMatch("HOST_PATH"::equals));
    }
}

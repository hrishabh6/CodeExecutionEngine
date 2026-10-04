package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import io.fabric8.kubernetes.api.model.CapabilitiesBuilder;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.PodSecurityContextBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecurityContextBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.KubernetesExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.PlaygroundSandboxProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds hardened one-shot Kubernetes Jobs for isolated code execution.
 */
public final class SandboxedKubernetesJobBuilder {

    private SandboxedKubernetesJobBuilder() {
    }

    public static Job buildJob(
            String jobName,
            String namespace,
            String submissionId,
            String executionId,
            String payloadBase64,
            Map<String, String> extraEnv,
            KubernetesExecutionProperties kubernetesProperties,
            PlaygroundSandboxProperties sandboxProperties,
            boolean playgroundJob) {

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", "code-execution-engine");
        labels.put("component", playgroundJob ? "playground-execution-job" : "execution-job");
        labels.put("submission-id", sanitizeLabelValue(submissionId));
        labels.put("execution-id", sanitizeLabelValue(executionId));
        if (playgroundJob) {
            labels.put("execution-mode", "playground");
        }

        ResourceRequirementsBuilder resourceRequirements = new ResourceRequirementsBuilder();
        applyResource(resourceRequirements, "cpu", kubernetesProperties.getResources().getRequests().getCpu(), true);
        applyResource(resourceRequirements, "memory", kubernetesProperties.getResources().getRequests().getMemory(), true);
        applyResource(resourceRequirements, "cpu", kubernetesProperties.getResources().getLimits().getCpu(), false);
        applyResource(resourceRequirements, "memory", kubernetesProperties.getResources().getLimits().getMemory(), false);

        SecurityContextBuilder securityContext = new SecurityContextBuilder()
                .withRunAsNonRoot(true)
                .withRunAsUser(sandboxProperties.getRunAsUser())
                .withRunAsGroup(sandboxProperties.getRunAsGroup())
                .withAllowPrivilegeEscalation(false)
                .withReadOnlyRootFilesystem(true)
                .withCapabilities(new CapabilitiesBuilder().withDrop("ALL").build());

        ContainerBuilder container = new ContainerBuilder()
                .withName("executor")
                .withImage(kubernetesProperties.getJobImage())
                .withImagePullPolicy(kubernetesProperties.getImagePullPolicy())
                .addNewEnv().withName("EXECUTION_MODE").withValue("job-runner").endEnv()
                .addNewEnv().withName("EXECUTION_JOB_PAYLOAD_B64").withValue(payloadBase64).endEnv()
                .addNewEnv().withName("SPRING_MAIN_WEB_APPLICATION_TYPE").withValue("none").endEnv()
                .withResources(resourceRequirements.build())
                .withSecurityContext(securityContext.build())
                .withVolumeMounts(
                        new VolumeMountBuilder().withName("tmp").withMountPath("/tmp").build(),
                        new VolumeMountBuilder().withName("workspace").withMountPath("/workspace").build());

        if (extraEnv != null) {
            extraEnv.forEach((k, v) -> container.addNewEnv().withName(k).withValue(v).endEnv());
        }

        JobBuilder builder = new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withNamespace(namespace)
                .addToLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(0)
                .withActiveDeadlineSeconds(kubernetesProperties.getActiveDeadlineSeconds())
                .withTtlSecondsAfterFinished(kubernetesProperties.getTtlSecondsAfterFinished())
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withAutomountServiceAccountToken(false)
                .withSecurityContext(new PodSecurityContextBuilder()
                        .withRunAsNonRoot(true)
                        .withRunAsUser(sandboxProperties.getRunAsUser())
                        .withRunAsGroup(sandboxProperties.getRunAsGroup())
                        .withFsGroup(sandboxProperties.getFsGroup())
                        .build())
                .withVolumes(
                        new VolumeBuilder().withName("tmp").withNewEmptyDir().endEmptyDir().build(),
                        new VolumeBuilder().withName("workspace").withNewEmptyDir().endEmptyDir().build())
                .withContainers(container.build())
                .endSpec()
                .endTemplate()
                .endSpec();

        String serviceAccount = playgroundJob
                ? sandboxProperties.getJobServiceAccountName()
                : kubernetesProperties.getServiceAccountName();
        if (serviceAccount != null && !serviceAccount.isBlank()) {
            builder.editSpec().editTemplate().editSpec().withServiceAccountName(serviceAccount).endSpec().endTemplate()
                    .endSpec();
        }

        return builder.build();
    }

    public static Job buildProfileJob(
            String jobName,
            String namespace,
            String submissionId,
            String executionId,
            Map<String, String> extraEnv,
            KubernetesExecutionProperties kubernetesProperties,
            ComplexityProfileExecutionProperties profileProperties) {

        ComplexityProfileExecutionProperties.Sandbox sandbox = profileProperties.getSandbox();
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", "code-execution-engine");
        labels.put("component", "complexity-profile-job");
        labels.put("submission-id", sanitizeLabelValue(submissionId));
        labels.put("execution-id", sanitizeLabelValue(executionId));
        labels.put("execution-mode", "complexity-profile");

        ComplexityProfileExecutionProperties.JobResources resources = sandbox.getJobResources();
        ResourceRequirementsBuilder resourceRequirements = new ResourceRequirementsBuilder();
        applyResource(resourceRequirements, "cpu", resources.getCpuRequest(), true);
        applyResource(resourceRequirements, "memory", resources.getMemoryRequest(), true);
        applyResource(resourceRequirements, "ephemeral-storage", resources.getEphemeralStorageRequest(), true);
        applyResource(resourceRequirements, "cpu", resources.getCpuLimit(), false);
        applyResource(resourceRequirements, "memory", resources.getMemoryLimit(), false);
        applyResource(resourceRequirements, "ephemeral-storage", resources.getEphemeralStorageLimit(), false);

        SecurityContextBuilder securityContext = new SecurityContextBuilder()
                .withRunAsNonRoot(true)
                .withRunAsUser(sandbox.getRunAsUser())
                .withRunAsGroup(sandbox.getRunAsGroup())
                .withAllowPrivilegeEscalation(false)
                .withReadOnlyRootFilesystem(true)
                .withCapabilities(new CapabilitiesBuilder().withDrop("ALL").build())
                .withNewSeccompProfile().withType("RuntimeDefault").endSeccompProfile();

        ContainerBuilder container = new ContainerBuilder()
                .withName("profile-runner")
                .withImage(kubernetesProperties.getJobImage())
                .withImagePullPolicy(kubernetesProperties.getImagePullPolicy())
                .addNewEnv().withName("EXECUTION_MODE").withValue("job-runner").endEnv()
                .addNewEnv().withName("EXECUTION_JOB_KIND").withValue("COMPLEXITY_PROFILE").endEnv()
                .addNewEnv().withName("COMPLEXITY_PROFILE_EXECUTION_ID").withValue(executionId).endEnv()
                .addNewEnv().withName("SPRING_MAIN_WEB_APPLICATION_TYPE").withValue("none").endEnv()
                .addNewEnv().withName("EXECUTION_COMPLEXITY_PROFILE_HARNESS_USE_CHILD_JVM").withValue("false").endEnv()
                .withResources(resourceRequirements.build())
                .withSecurityContext(securityContext.build())
                .withVolumeMounts(
                        new VolumeMountBuilder().withName("tmp").withMountPath("/tmp").build(),
                        new VolumeMountBuilder().withName("workspace").withMountPath("/workspace").build());

        if (extraEnv != null) {
            extraEnv.forEach((k, v) -> container.addNewEnv().withName(k).withValue(v).endEnv());
        }
        addSandboxRedisCredentialEnvFromSecret(container, profileProperties);

        JobBuilder builder = new JobBuilder()
                .withNewMetadata()
                .withName(jobName)
                .withNamespace(namespace)
                .addToLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(0)
                .withActiveDeadlineSeconds(sandbox.getJobActiveDeadlineSeconds())
                .withTtlSecondsAfterFinished(sandbox.getTtlSecondsAfterFinished())
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(labels)
                .endMetadata()
                .withNewSpec()
                .withRestartPolicy("Never")
                .withAutomountServiceAccountToken(false)
                .withTerminationGracePeriodSeconds(sandbox.getTerminationGracePeriodSeconds())
                .withSecurityContext(new PodSecurityContextBuilder()
                        .withRunAsNonRoot(true)
                        .withRunAsUser(sandbox.getRunAsUser())
                        .withRunAsGroup(sandbox.getRunAsGroup())
                        .withFsGroup(sandbox.getFsGroup())
                        .build())
                .withVolumes(
                        new VolumeBuilder().withName("tmp").withNewEmptyDir().endEmptyDir().build(),
                        new VolumeBuilder().withName("workspace").withNewEmptyDir().endEmptyDir().build())
                .withContainers(container.build())
                .endSpec()
                .endTemplate()
                .endSpec();

        String serviceAccount = sandbox.getJobServiceAccountName();
        if (serviceAccount != null && !serviceAccount.isBlank()) {
            builder.editSpec().editTemplate().editSpec().withServiceAccountName(serviceAccount).endSpec().endTemplate()
                    .endSpec();
        }

        return builder.build();
    }

    private static void addSandboxRedisCredentialEnvFromSecret(
            ContainerBuilder container, ComplexityProfileExecutionProperties profileProperties) {
        ComplexityProfileExecutionProperties.Sandbox sandbox = profileProperties.getSandbox();
        String secretName = sandbox.getSandboxRedisCredentialsSecretName();
        if (secretName == null || secretName.isBlank()) {
            return;
        }
        container.addNewEnv()
                .withName("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_USERNAME")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(secretName)
                .withKey(sandbox.getSandboxRedisUsernameSecretKey())
                .withOptional(false)
                .endSecretKeyRef()
                .endValueFrom()
                .endEnv();
        container.addNewEnv()
                .withName("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_PASSWORD")
                .withNewValueFrom()
                .withNewSecretKeyRef()
                .withName(secretName)
                .withKey(sandbox.getSandboxRedisPasswordSecretKey())
                .withOptional(false)
                .endSecretKeyRef()
                .endValueFrom()
                .endEnv();
    }

    private static void applyResource(
            ResourceRequirementsBuilder builder, String name, String value, boolean request) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (request) {
            builder.addToRequests(name, new Quantity(value));
        } else {
            builder.addToLimits(name, new Quantity(value));
        }
    }

    private static String sanitizeLabelValue(String value) {
        String normalized = value == null ? "unknown" : value.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
        if (normalized.length() > 63) {
            normalized = normalized.substring(0, 63);
        }
        return normalized;
    }
}

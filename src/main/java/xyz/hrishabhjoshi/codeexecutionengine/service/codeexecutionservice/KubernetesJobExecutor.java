package xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.KubernetesExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.service.helperservice.ExecutionJobResultStore;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class KubernetesJobExecutor implements CodeExecutor {

    private final KubernetesClient kubernetesClient;
    private final KubernetesExecutionProperties kubernetesProperties;
    private final ExecutionPayloadCodec payloadCodec;
    private final ExecutionJobResultStore resultStore;

    @Override
    public CodeExecutionResultDTO execute(
            CodeSubmissionDTO submissionDto,
            String submissionId,
            String executionId,
            Path submissionRootPath,
            String fullyQualifiedMainClass,
            String language,
            Consumer<String> logConsumer) {

        validateConfiguration();

        String jobName = buildJobName(executionId);
        String namespace = kubernetesProperties.getNamespace();
        String payload = payloadCodec.encode(submissionDto);

        log.info("[K8S_EXECUTOR] submissionId={} executionId={} jobName={} language={}",
                submissionId, executionId, jobName, language);

        Job job = buildJob(jobName, namespace, submissionId, executionId, payload);

        logConsumer.accept("K8S_EXECUTOR: Kubernetes Job backend selected");
        logConsumer.accept("K8S_EXECUTOR: Creating job " + jobName + " in namespace " + namespace);
        kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(job).create();

        try {
            Optional<CodeExecutionResultDTO> result = resultStore.await(
                    executionId,
                    Duration.ofSeconds(kubernetesProperties.getJobCompletionTimeoutSeconds()),
                    Duration.ofMillis(kubernetesProperties.getResultPollIntervalMillis()));

            if (result.isPresent()) {
                logConsumer.accept("K8S_EXECUTOR: Received result for executionId " + executionId);
                resultStore.delete(executionId);
                return result.get();
            }

            String diagnostics = collectDiagnostics(namespace, jobName);
            logConsumer.accept("K8S_EXECUTOR: Timed out waiting for Kubernetes job result");
            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .executionId(executionId)
                    .overallStatus(Status.INTERNAL_ERROR)
                    .compilationOutput("Timed out waiting for Kubernetes Job result. " + diagnostics)
                    .testCaseOutputs(List.of())
                    .build();
        } finally {
            if (kubernetesProperties.isDeleteJobAfterRead()) {
                deleteJob(namespace, jobName);
            }
        }
    }

    private String buildJobName(String executionId) {
        String normalized = executionId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        if (normalized.length() > 40) {
            normalized = normalized.substring(0, 40);
        }
        return "cxe-" + normalized;
    }

    private void validateConfiguration() {
        if (kubernetesProperties.getJobImage() == null || kubernetesProperties.getJobImage().isBlank()) {
            throw new IllegalStateException("execution.kubernetes.job-image must be configured for kubernetes-job backend");
        }
    }

    private Job buildJob(String jobName, String namespace, String submissionId, String executionId, String payload) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("app", "code-execution-engine");
        labels.put("component", "execution-job");
        labels.put("submission-id", sanitizeLabelValue(submissionId));
        labels.put("execution-id", sanitizeLabelValue(executionId));

        ResourceRequirementsBuilder resourceRequirements = new ResourceRequirementsBuilder();
        if (kubernetesProperties.getResources().getRequests().getCpu() != null
                && !kubernetesProperties.getResources().getRequests().getCpu().isBlank()) {
            resourceRequirements.addToRequests("cpu", new Quantity(kubernetesProperties.getResources().getRequests().getCpu()));
        }
        if (kubernetesProperties.getResources().getRequests().getMemory() != null
                && !kubernetesProperties.getResources().getRequests().getMemory().isBlank()) {
            resourceRequirements.addToRequests("memory", new Quantity(kubernetesProperties.getResources().getRequests().getMemory()));
        }
        if (kubernetesProperties.getResources().getLimits().getCpu() != null
                && !kubernetesProperties.getResources().getLimits().getCpu().isBlank()) {
            resourceRequirements.addToLimits("cpu", new Quantity(kubernetesProperties.getResources().getLimits().getCpu()));
        }
        if (kubernetesProperties.getResources().getLimits().getMemory() != null
                && !kubernetesProperties.getResources().getLimits().getMemory().isBlank()) {
            resourceRequirements.addToLimits("memory", new Quantity(kubernetesProperties.getResources().getLimits().getMemory()));
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
                            .addNewContainer()
                                .withName("executor")
                                .withImage(kubernetesProperties.getJobImage())
                                .withImagePullPolicy(kubernetesProperties.getImagePullPolicy())
                                .addNewEnv().withName("EXECUTION_MODE").withValue("job-runner").endEnv()
                                .addNewEnv().withName("EXECUTION_JOB_PAYLOAD_B64").withValue(payload).endEnv()
                                .addNewEnv().withName("EXECUTION_BACKEND").withValue("worker-pod").endEnv()
                                .addNewEnv().withName("SPRING_MAIN_WEB_APPLICATION_TYPE").withValue("none").endEnv()
                                .withResources(resourceRequirements.build())
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec();

        if (kubernetesProperties.getServiceAccountName() != null
                && !kubernetesProperties.getServiceAccountName().isBlank()) {
            builder.editSpec()
                    .editTemplate()
                    .editSpec()
                    .withServiceAccountName(kubernetesProperties.getServiceAccountName())
                    .endSpec()
                    .endTemplate()
                    .endSpec();
        }

        return builder.build();
    }

    private String collectDiagnostics(String namespace, String jobName) {
        try {
            List<Pod> pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withLabel("job-name", jobName)
                    .list()
                    .getItems();

            if (pods.isEmpty()) {
                return "No pods found for job " + jobName;
            }

            StringBuilder diagnostics = new StringBuilder();
            for (Pod pod : pods) {
                String podName = pod.getMetadata() != null ? pod.getMetadata().getName() : "unknown-pod";
                String phase = pod.getStatus() != null ? pod.getStatus().getPhase() : "unknown";
                diagnostics.append("pod=").append(podName).append(", phase=").append(phase);

                String logs = kubernetesClient.pods().inNamespace(namespace).withName(podName).getLog();
                if (logs != null && !logs.isBlank()) {
                    diagnostics.append(", logs=").append(truncate(logs));
                }
                diagnostics.append("; ");
            }
            return diagnostics.toString();
        } catch (Exception e) {
            log.warn("[K8S_EXECUTOR] Failed to collect diagnostics for job {}: {}", jobName, e.getMessage());
            return "Failed to collect diagnostics: " + e.getMessage();
        }
    }

    private void deleteJob(String namespace, String jobName) {
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
        } catch (Exception e) {
            log.warn("[K8S_EXECUTOR] Failed to delete job {}: {}", jobName, e.getMessage());
        }
    }

    private String sanitizeLabelValue(String value) {
        String normalized = value == null ? "unknown" : value.toLowerCase().replaceAll("[^a-z0-9.-]", "-");
        if (normalized.length() > 63) {
            normalized = normalized.substring(0, 63);
        }
        return normalized;
    }

    private String truncate(String value) {
        int maxLength = 1200;
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}

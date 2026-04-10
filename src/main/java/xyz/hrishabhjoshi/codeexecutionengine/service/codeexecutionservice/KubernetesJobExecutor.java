package xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
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
import java.nio.charset.StandardCharsets;
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

        String baseJobName = buildJobName(executionId);
        String namespace = kubernetesProperties.getNamespace();
        String payload = payloadCodec.encode(submissionDto);
        int payloadSizeBytes = payload.getBytes(StandardCharsets.UTF_8).length;

        if (payloadSizeBytes > kubernetesProperties.getMaxPayloadBytes()) {
            String message = "Execution payload exceeds configured Kubernetes Job env limit: payloadBytes="
                    + payloadSizeBytes + ", maxPayloadBytes=" + kubernetesProperties.getMaxPayloadBytes();
            log.error("[K8S_EXECUTOR] submissionId={} executionId={} payload too large: {}",
                    submissionId, executionId, message);
            logConsumer.accept("K8S_EXECUTOR: " + message);
            return buildInfrastructureFailure(submissionId, executionId, message);
        }

        int maxAttempts = Math.max(1, kubernetesProperties.getMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String jobName = buildAttemptJobName(baseJobName, attempt);
            log.info("[K8S_EXECUTOR] submissionId={} executionId={} attempt={}/{} jobName={} language={} payloadBytes={}",
                    submissionId, executionId, attempt, maxAttempts, jobName, language, payloadSizeBytes);

            Job job = buildJob(jobName, namespace, submissionId, executionId, payload);
            logConsumer.accept("K8S_EXECUTOR: attempt " + attempt + "/" + maxAttempts
                    + " creating job " + jobName + " in namespace " + namespace);

            boolean created = false;
            try {
                kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(job).create();
                created = true;

                CodeExecutionResultDTO result = waitForResultOrTerminalFailure(
                        submissionId, executionId, namespace, jobName, logConsumer);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                String message = "Failed to submit Kubernetes Job jobName=" + jobName + ": " + e.getMessage();
                log.error("[K8S_EXECUTOR] submissionId={} executionId={} attempt={} jobName={} submit failed: {}",
                        submissionId, executionId, attempt, jobName, e.getMessage(), e);
                logConsumer.accept("K8S_EXECUTOR: " + message);
                if (attempt == maxAttempts) {
                    return buildInfrastructureFailure(submissionId, executionId, message);
                }
            } finally {
                if (created && kubernetesProperties.isDeleteJobAfterRead()) {
                    deleteJob(namespace, jobName, executionId);
                }
            }

            if (attempt < maxAttempts) {
                sleepBeforeRetry(executionId, jobName, attempt, logConsumer);
            }
        }

        return buildInfrastructureFailure(
                submissionId,
                executionId,
                "Kubernetes Job execution failed after " + maxAttempts + " attempts");
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

    private String buildAttemptJobName(String baseJobName, int attempt) {
        if (attempt <= 1) {
            return baseJobName;
        }

        String suffix = "-a" + attempt;
        int maxBaseLength = Math.max(1, 63 - suffix.length());
        String truncatedBase = baseJobName.length() > maxBaseLength
                ? baseJobName.substring(0, maxBaseLength)
                : baseJobName;
        return truncatedBase + suffix;
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

                String podReason = pod.getStatus() != null ? pod.getStatus().getReason() : null;
                if (podReason != null && !podReason.isBlank()) {
                    diagnostics.append(", reason=").append(podReason);
                }

                if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
                    pod.getStatus().getContainerStatuses().forEach(status -> {
                        if (status.getState() != null && status.getState().getWaiting() != null) {
                            diagnostics.append(", waitingReason=")
                                    .append(status.getState().getWaiting().getReason());
                        }
                        if (status.getState() != null && status.getState().getWaiting() != null
                                && status.getState().getWaiting().getMessage() != null) {
                            diagnostics.append(", waitingMessage=")
                                    .append(truncate(status.getState().getWaiting().getMessage()));
                        }
                        if (status.getState() != null && status.getState().getTerminated() != null) {
                            diagnostics.append(", terminatedReason=")
                                    .append(status.getState().getTerminated().getReason());
                            diagnostics.append(", exitCode=")
                                    .append(status.getState().getTerminated().getExitCode());
                        }
                    });
                }

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

    private void deleteJob(String namespace, String jobName, String executionId) {
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
        } catch (Exception e) {
            log.warn("[K8S_EXECUTOR] executionId={} failed to delete job {}: {}", executionId, jobName, e.getMessage());
        }
    }

    private CodeExecutionResultDTO waitForResultOrTerminalFailure(
            String submissionId,
            String executionId,
            String namespace,
            String jobName,
            Consumer<String> logConsumer) {

        long pollIntervalMillis = Math.max(250, kubernetesProperties.getResultPollIntervalMillis());
        long deadline = System.nanoTime() + Duration.ofSeconds(kubernetesProperties.getJobCompletionTimeoutSeconds()).toNanos();

        while (System.nanoTime() < deadline) {
            Optional<CodeExecutionResultDTO> result = resultStore.get(executionId);
            if (result.isPresent()) {
                log.info("[K8S_EXECUTOR] submissionId={} executionId={} jobName={} received Redis result",
                        submissionId, executionId, jobName);
                resultStore.delete(executionId);
                return result.get();
            }

            Job currentJob = kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
            if (isFailed(currentJob)) {
                String diagnostics = collectDiagnostics(namespace, jobName);
                String message = "Kubernetes Job failed before writing result. executionId=" + executionId
                        + ", jobName=" + jobName + ". " + diagnostics;
                log.error("[K8S_EXECUTOR] {}", message);
                logConsumer.accept("K8S_EXECUTOR: " + message);
                return buildInfrastructureFailure(submissionId, executionId, message);
            }

            sleep(pollIntervalMillis);
        }

        String diagnostics = collectDiagnostics(namespace, jobName);
        String message = "Timed out waiting for Kubernetes Job result. executionId=" + executionId
                + ", jobName=" + jobName + ". " + diagnostics;
        log.error("[K8S_EXECUTOR] {}", message);
        logConsumer.accept("K8S_EXECUTOR: " + message);
        return buildInfrastructureFailure(submissionId, executionId, message);
    }

    private boolean isFailed(Job job) {
        if (job == null) {
            return false;
        }

        JobStatus status = job.getStatus();
        if (status == null) {
            return false;
        }

        if (status.getFailed() != null && status.getFailed() > 0) {
            return true;
        }

        if (status.getConditions() == null) {
            return false;
        }

        return status.getConditions().stream()
                .anyMatch(condition -> "Failed".equalsIgnoreCase(condition.getType())
                        && "True".equalsIgnoreCase(condition.getStatus()));
    }

    private void sleepBeforeRetry(String executionId, String jobName, int attempt, Consumer<String> logConsumer) {
        long retryDelayMillis = Math.max(0, kubernetesProperties.getRetryDelayMillis());
        if (retryDelayMillis == 0) {
            return;
        }

        log.info("[K8S_EXECUTOR] executionId={} jobName={} retrying after attempt={} delay={}ms",
                executionId, jobName, attempt, retryDelayMillis);
        logConsumer.accept("K8S_EXECUTOR: retrying after failed attempt " + attempt
                + " for job " + jobName + " in " + retryDelayMillis + "ms");
        sleep(retryDelayMillis);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Kubernetes Job result", e);
        }
    }

    private CodeExecutionResultDTO buildInfrastructureFailure(String submissionId, String executionId, String message) {
        return CodeExecutionResultDTO.builder()
                .submissionId(submissionId)
                .executionId(executionId)
                .overallStatus(Status.INTERNAL_ERROR)
                .compilationOutput(message)
                .testCaseOutputs(List.of())
                .build();
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

package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.KubernetesExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.PlaygroundSandboxProperties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionMode;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionRequest;
import xyz.hrishabhjoshi.codeexecutionengine.dto.RawExecutionResult;
import xyz.hrishabhjoshi.codeexecutionengine.dto.RawExecutionStatus;
import xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice.ExecutionPayloadCodec;
import xyz.hrishabhjoshi.codeexecutionengine.service.helperservice.PlaygroundJobResultStore;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundKubernetesJobService {

    private final KubernetesClient kubernetesClient;
    private final KubernetesExecutionProperties kubernetesProperties;
    private final PlaygroundSandboxProperties sandboxProperties;
    private final ExecutionPayloadCodec payloadCodec;
    private final PlaygroundJobResultStore playgroundJobResultStore;

    @Value("${spring.data.redis.host:redis}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private String redisPort;

    public boolean isConfigured() {
        return kubernetesProperties.getJobImage() != null
                && !kubernetesProperties.getJobImage().isBlank()
                && sandboxProperties.getJobServiceAccountName() != null
                && !sandboxProperties.getJobServiceAccountName().isBlank();
    }

    public RawExecutionResult runIsolated(ExecutionRequest request) {
        if (!isConfigured()) {
            return internalError("Playground kubernetes-job sandbox is not configured");
        }

        String submissionId = request.getSubmissionId();
        String executionId = request.getExecutionId();
        String namespace = kubernetesProperties.getNamespace();
        CodeSubmissionDTO payloadDto = CodeSubmissionDTO.builder()
                .submissionId(submissionId)
                .executionId(executionId)
                .executionMode(ExecutionMode.PLAYGROUND.name())
                .language(request.getLanguage())
                .userSolutionCode(request.getCode())
                .stdin(request.getStdin())
                .build();

        String payload = payloadCodec.encode(payloadDto);
        int payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > kubernetesProperties.getMaxPayloadBytes()) {
            return internalError("Playground payload exceeds job size limit");
        }

        String jobName = buildJobName(executionId);
        Map<String, String> env = new LinkedHashMap<>();
        env.put("EXECUTION_JOB_KIND", "PLAYGROUND");
        env.put("EXECUTION_BACKEND", "worker-pod");
        env.put("SPRING_DATA_REDIS_HOST", redisHost);
        env.put("SPRING_DATA_REDIS_PORT", redisPort);
        env.put("JAVA_TOOL_OPTIONS", "-Djava.io.tmpdir=/tmp");

        Job job = SandboxedKubernetesJobBuilder.buildJob(
                jobName,
                namespace,
                submissionId,
                executionId,
                payload,
                env,
                kubernetesProperties,
                sandboxProperties,
                true);

        boolean created = false;
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(job).create();
            created = true;
            return waitForResult(executionId, namespace, jobName);
        } catch (Exception e) {
            log.error("[PLAYGROUND_K8S] executionId={} job submit failed: {}", executionId, e.getMessage(), e);
            return internalError("Failed to start isolated execution");
        } finally {
            if (created && kubernetesProperties.isDeleteJobAfterRead()) {
                deleteJob(namespace, jobName, executionId);
            }
        }
    }

    private RawExecutionResult waitForResult(String executionId, String namespace, String jobName) {
        long pollIntervalMillis = Math.max(250, kubernetesProperties.getResultPollIntervalMillis());
        long deadline = System.nanoTime()
                + Duration.ofSeconds(kubernetesProperties.getJobCompletionTimeoutSeconds()).toNanos();

        while (System.nanoTime() < deadline) {
            var result = playgroundJobResultStore.get(executionId);
            if (result.isPresent()) {
                playgroundJobResultStore.delete(executionId);
                return result.get();
            }

            Job currentJob = kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
            if (isFailed(currentJob)) {
                return internalError("Isolated execution job failed");
            }

            sleep(pollIntervalMillis);
        }
        return internalError("Timed out waiting for isolated execution");
    }

    private boolean isFailed(Job job) {
        if (job == null || job.getStatus() == null) {
            return false;
        }
        JobStatus status = job.getStatus();
        if (status.getFailed() != null && status.getFailed() > 0) {
            return true;
        }
        if (status.getConditions() == null) {
            return false;
        }
        return status.getConditions().stream()
                .anyMatch(c -> "Failed".equalsIgnoreCase(c.getType()) && "True".equalsIgnoreCase(c.getStatus()));
    }

    private void deleteJob(String namespace, String jobName, String executionId) {
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).delete();
        } catch (Exception e) {
            log.warn("[PLAYGROUND_K8S] executionId={} failed to delete job {}: {}", executionId, jobName, e.getMessage());
        }
    }

    private String buildJobName(String executionId) {
        String normalized = executionId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        if (normalized.length() > 36) {
            normalized = normalized.substring(0, 36);
        }
        return "pg-" + normalized;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for playground job", e);
        }
    }

    private RawExecutionResult internalError(String message) {
        return RawExecutionResult.builder()
                .status(RawExecutionStatus.INTERNAL_ERROR)
                .stderr(message)
                .runtimeMs(0)
                .outputTruncated(false)
                .build();
    }
}

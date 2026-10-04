package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxMeasurementResult;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileTrustedOracleBundle;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.metrics.ComplexityProfileMetrics;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.support.ComplexityProfileEnvironmentFingerprint;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.KubernetesExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.service.execution.SandboxedKubernetesJobBuilder;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexityProfileKubernetesJobService {

    private final KubernetesClient kubernetesClient;
    private final KubernetesExecutionProperties kubernetesProperties;
    private final ComplexityProfileExecutionProperties profileProperties;
    private final ComplexityProfileQueueService queueService;
    private final ComplexityProfileActiveJobGuard activeJobGuard;
    private final ComplexityProfileJobSlotRegistry slotRegistry;
    private final ComplexityProfileSandboxPayloadFactory sandboxPayloadFactory;
    private final ComplexityProfileSandboxRedisStore sandboxRedisStore;
    private final ComplexityProfileTrustedOracleStore oracleStore;
    private final ComplexityProfileTrustedResultAssembler resultAssembler;
    private final ComplexityProfileMetrics complexityProfileMetrics;

    @Value("${spring.data.redis.host:redis}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private String redisPort;

    public boolean isConfigured() {
        return kubernetesProperties.getJobImage() != null
                && !kubernetesProperties.getJobImage().isBlank()
                && profileProperties.getSandbox().getJobServiceAccountName() != null
                && !profileProperties.getSandbox().getJobServiceAccountName().isBlank()
                && sandboxRedisConfigured();
    }

    public void runIsolated(ComplexityProfileJobPayload job) {
        String executionId = job.getExecutionId();
        if (!isConfigured()) {
            storeWorkerFailure(executionId, "PROFILE_K8S_NOT_CONFIGURED");
            return;
        }
        Optional<Integer> slot = slotRegistry.tryAcquire(executionId);
        if (slot.isEmpty()) {
            complexityProfileMetrics.recordConcurrencyCapRejection();
            storeWorkerFailure(executionId, "PROFILE_CONCURRENT_JOB_CAP");
            return;
        }
        if (!activeJobGuard.hasCapacity()) {
            slotRegistry.release(executionId, slot);
            complexityProfileMetrics.recordConcurrencyCapRejection();
            storeWorkerFailure(executionId, "PROFILE_CONCURRENT_JOB_CAP");
            return;
        }

        oracleStore.save(sandboxPayloadFactory.toOracleBundle(job));
        sandboxRedisStore.stageSandboxPayload(sandboxPayloadFactory.toSandboxJobPayload(job));

        String namespace = kubernetesProperties.getNamespace();
        String jobName = buildJobName(executionId);
        Map<String, String> env = sandboxJobEnv();

        Job k8sJob = SandboxedKubernetesJobBuilder.buildProfileJob(
                jobName,
                namespace,
                job.getSubmissionId(),
                executionId,
                env,
                kubernetesProperties,
                profileProperties);

        boolean created = false;
        try {
            kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(k8sJob).create();
            created = true;
            Optional<ComplexityProfileSandboxMeasurementResult> measurement = waitForSandboxMeasurement(executionId, namespace, jobName);
            if (measurement.isEmpty()) {
                storeWorkerFailure(executionId, "PROFILE_K8S_TIMEOUT");
                return;
            }
            Optional<ComplexityProfileTrustedOracleBundle> oracle = oracleStore.load(executionId);
            if (oracle.isEmpty()) {
                storeWorkerFailure(executionId, "PROFILE_ORACLE_MISSING");
                return;
            }
            ComplexityProfilePollResponse terminal = resultAssembler.assemble(measurement.get(), oracle.get());
            queueService.storeResult(executionId, terminal);
        } catch (Exception e) {
            log.error("[PROFILE-K8S] executionId={} submit failed: {}", executionId, e.getMessage(), e);
            storeWorkerFailure(executionId, "PROFILE_K8S_SUBMIT_FAILED");
        } finally {
            slotRegistry.release(executionId, slot);
            oracleStore.delete(executionId);
            sandboxRedisStore.deleteSandboxPayload(executionId);
            sandboxRedisStore.deleteSandboxMeasurement(executionId);
            if (created && kubernetesProperties.isDeleteJobAfterRead()) {
                deleteJob(namespace, jobName, executionId);
            }
        }
    }

    private Map<String, String> sandboxJobEnv() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_HOST", redisHost);
        env.put("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_PORT", redisPort);
        if (!StringUtils.hasText(profileProperties.getSandbox().getSandboxRedisCredentialsSecretName())) {
            env.put("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_USERNAME",
                    profileProperties.getSandbox().getSandboxRedisUsername());
            env.put("EXECUTION_COMPLEXITY_PROFILE_SANDBOX_REDIS_PASSWORD",
                    profileProperties.getSandbox().getSandboxRedisPassword());
        }
        env.put("JAVA_TOOL_OPTIONS", "-Djava.io.tmpdir=/tmp -Djava.awt.headless=true");
        return env;
    }

    private boolean sandboxRedisConfigured() {
        if (!profileProperties.getSandbox().isRequireSandboxRedisCredentials()) {
            return true;
        }
        return StringUtils.hasText(profileProperties.getSandbox().getSandboxRedisUsername())
                && StringUtils.hasText(profileProperties.getSandbox().getSandboxRedisPassword());
    }

    private Optional<ComplexityProfileSandboxMeasurementResult> waitForSandboxMeasurement(
            String executionId, String namespace, String jobName) {
        long pollIntervalMillis = Math.max(250, kubernetesProperties.getResultPollIntervalMillis());
        long timeoutSeconds = profileProperties.getSandbox().getJobCompletionTimeoutSeconds();
        long deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos();

        while (System.nanoTime() < deadline) {
            Optional<ComplexityProfileSandboxMeasurementResult> result = sandboxRedisStore.loadSandboxMeasurement(executionId);
            if (result.isPresent()) {
                return result;
            }
            Job currentJob = kubernetesClient.batch().v1().jobs().inNamespace(namespace).withName(jobName).get();
            if (isFailed(currentJob)) {
                return Optional.empty();
            }
            sleep(pollIntervalMillis);
        }
        return Optional.empty();
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
            log.warn("[PROFILE-K8S] executionId={} failed to delete job {}: {}", executionId, jobName, e.getMessage());
        }
    }

    private String buildJobName(String executionId) {
        String normalized = executionId.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        if (normalized.length() > 36) {
            normalized = normalized.substring(0, 36);
        }
        return "cp-" + normalized;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for profile job", e);
        }
    }

    private void storeWorkerFailure(String executionId, String errorCode) {
        queueService.storeResult(executionId, new ComplexityProfilePollResponse(
                executionId,
                "COMPLETED",
                "FAILED",
                System.getProperty("java.version"),
                profileProperties.getHarnessVersion(),
                profileProperties.getMeasurementPolicyVersion(),
                "cxe-kubernetes-job-v1",
                ComplexityProfileEnvironmentFingerprint.build(profileProperties),
                java.util.List.of(),
                errorCode,
                errorCode));
    }
}

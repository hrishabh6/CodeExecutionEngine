package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfilePollResponse;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness.ComplexityProfileJavaHarness;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness.ComplexityProfileJavaHarness.HarnessRunResult;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.support.ComplexityProfileEnvironmentFingerprint;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplexityProfileWorkerService {

    private final ComplexityProfileQueueService queueService;
    private final ComplexityProfileJavaHarness harness;
    private final ComplexityProfileKubernetesJobService kubernetesJobService;
    private final ComplexityProfileExecutionProperties properties;

    private volatile boolean running = true;

    @PreDestroy
    public void shutdown() {
        running = false;
    }

    @Async("executionWorkerExecutor")
    public void startWorker(String workerId) {
        log.info("[PROFILE-WORKER] {} starting", workerId);
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                ComplexityProfileJobPayload job = queueService.dequeue(properties.getWorker().getPollTimeoutSeconds());
                if (job == null) {
                    continue;
                }
                processJob(job, workerId);
            } catch (IllegalStateException e) {
                log.info("[PROFILE-WORKER] {} stopping (redis closed)", workerId);
                break;
            } catch (Exception e) {
                log.error("[PROFILE-WORKER] {} error: {}", workerId, e.getMessage(), e);
            }
        }
        log.info("[PROFILE-WORKER] {} stopped", workerId);
    }

    private void processJob(ComplexityProfileJobPayload job, String workerId) {
        String executionId = job.getExecutionId();
        log.info("[PROFILE-WORKER] {} processing executionId={}", workerId, executionId);
        queueService.setStatus(executionId, new ComplexityProfilePollResponse(
                executionId, "RUNNING", null, null, null, null, null, null, null, null, null));
        if (properties.getSandbox().isKubernetesJobBackend()) {
            kubernetesJobService.runIsolated(job);
            return;
        }
        HarnessRunResult result = harness.execute(job);
        String profilerRuntime = "cxe-local-process-v1";
        ComplexityProfilePollResponse terminal = new ComplexityProfilePollResponse(
                executionId,
                "COMPLETED",
                result.compileStatus(),
                System.getProperty("java.version"),
                properties.getHarnessVersion(),
                properties.getMeasurementPolicyVersion(),
                profilerRuntime,
                ComplexityProfileEnvironmentFingerprint.build(properties),
                result.cases(),
                result.compileStatus().equals("SUCCESS") ? null : "COMPILE_FAILED",
                result.errorMessage());
        queueService.storeResult(executionId, terminal);
    }
}

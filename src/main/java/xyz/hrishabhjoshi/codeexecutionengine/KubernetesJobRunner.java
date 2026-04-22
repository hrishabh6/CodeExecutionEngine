package xyz.hrishabhjoshi.codeexecutionengine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice.ExecutionPayloadCodec;
import xyz.hrishabhjoshi.codeexecutionengine.service.helperservice.ExecutionJobResultStore;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class KubernetesJobRunner implements CommandLineRunner {

    private final ExecutionPayloadCodec payloadCodec;
    private final CodeExecutionManager codeExecutionManager;
    private final ExecutionJobResultStore resultStore;
    private final ConfigurableApplicationContext applicationContext;

    @Value("${execution.mode:worker}")
    private String executionMode;

    @Value("${execution.job.payload-b64:}")
    private String payloadBase64;

    @Override
    public void run(String... args) {
        if (!"job-runner".equalsIgnoreCase(executionMode)) {
            return;
        }

        int exitCode = 0;
        String executionId = "unknown";

        try {
            if (payloadBase64 == null || payloadBase64.isBlank()) {
                throw new IllegalStateException("execution.job.payload-b64 must be provided in job-runner mode");
            }

            CodeSubmissionDTO submission = payloadCodec.decode(payloadBase64);
            executionId = submission.getExecutionId();

            log.info("[JOB_RUNNER] Starting one-shot execution for submissionId={} executionId={}",
                    submission.getSubmissionId(), submission.getExecutionId());

            CodeExecutionResultDTO result = codeExecutionManager.runCodeWithTestcases(
                    submission,
                    logLine -> log.info("[JOB_RUNNER:{}] {}", submission.getExecutionId(), logLine));

            resultStore.save(submission.getExecutionId(), result);
            exitCode = result.getOverallStatus() == Status.INTERNAL_ERROR ? 1 : 0;
            log.info("[JOB_RUNNER] Stored result for executionId={} with status={}",
                    submission.getExecutionId(), result.getOverallStatus());
        } catch (Exception e) {
            exitCode = 1;
            log.error("[JOB_RUNNER] One-shot execution failed for executionId={}: {}", executionId, e.getMessage(), e);
        } finally {
            final int finalExitCode = exitCode;
            log.info("[JOB_RUNNER] Shutting down one-shot job runner with exitCode={}", exitCode);
            Thread shutdownThread = new Thread(() -> {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                applicationContext.close();
                System.exit(finalExitCode);
            }, "k8s-job-runner-shutdown");
            shutdownThread.setDaemon(false);
            shutdownThread.start();
        }
    }
}

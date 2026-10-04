package xyz.hrishabhjoshi.codeexecutionengine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.dto.*;
import xyz.hrishabhjoshi.codeexecutionengine.execution.PlaygroundProgramExecutor;
import xyz.hrishabhjoshi.codeexecutionengine.service.codeexecutionservice.ExecutionPayloadCodec;
import xyz.hrishabhjoshi.codeexecutionengine.service.helperservice.ExecutionJobResultStore;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service.ComplexityProfileKubernetesJobRunner;
import xyz.hrishabhjoshi.codeexecutionengine.service.helperservice.PlaygroundJobResultStore;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class KubernetesJobRunner implements CommandLineRunner {

    private final ExecutionPayloadCodec payloadCodec;
    private final CodeExecutionManager codeExecutionManager;
    private final ExecutionJobResultStore resultStore;
    private final PlaygroundJobResultStore playgroundJobResultStore;
    private final PlaygroundProgramExecutor playgroundProgramExecutor;
    private final ComplexityProfileKubernetesJobRunner complexityProfileKubernetesJobRunner;
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
            String jobKind = System.getenv("EXECUTION_JOB_KIND");
            if ("COMPLEXITY_PROFILE".equalsIgnoreCase(jobKind)) {
                String profileExecutionId = System.getenv("COMPLEXITY_PROFILE_EXECUTION_ID");
                if (profileExecutionId == null || profileExecutionId.isBlank()) {
                    throw new IllegalStateException("COMPLEXITY_PROFILE_EXECUTION_ID is required");
                }
                executionId = profileExecutionId;
                exitCode = complexityProfileKubernetesJobRunner.runOneShot(profileExecutionId);
                return;
            }

            if (payloadBase64 == null || payloadBase64.isBlank()) {
                throw new IllegalStateException("execution.job.payload-b64 must be provided in job-runner mode");
            }

            CodeSubmissionDTO submission = payloadCodec.decode(payloadBase64);
            executionId = submission.getExecutionId();

            log.info("[JOB_RUNNER] Starting one-shot execution for submissionId={} executionId={} mode={}",
                    submission.getSubmissionId(), submission.getExecutionId(), submission.getExecutionMode());

            if (ExecutionMode.PLAYGROUND.name().equalsIgnoreCase(submission.getExecutionMode())) {
                ExecutionRequest playgroundRequest = ExecutionRequest.builder()
                        .submissionId(submission.getSubmissionId())
                        .executionId(submission.getExecutionId())
                        .language(submission.getLanguage())
                        .code(submission.getUserSolutionCode())
                        .stdin(submission.getStdin())
                        .executionMode(ExecutionMode.PLAYGROUND.name())
                        .build();
                RawExecutionResult raw = playgroundProgramExecutor.execute(playgroundRequest);
                playgroundJobResultStore.save(submission.getExecutionId(), raw);
                exitCode = raw.getStatus() == RawExecutionStatus.INTERNAL_ERROR ? 1 : 0;
                log.info("[JOB_RUNNER] Stored playground result executionId={} status={}",
                        submission.getExecutionId(), raw.getStatus());
            } else {
                CodeExecutionResultDTO result = codeExecutionManager.runCodeWithTestcases(
                        submission,
                        logLine -> log.info("[JOB_RUNNER:{}] {}", submission.getExecutionId(), logLine));

                resultStore.save(submission.getExecutionId(), result);
                exitCode = result.getOverallStatus() == Status.INTERNAL_ERROR ? 1 : 0;
                log.info("[JOB_RUNNER] Stored result for executionId={} with status={}",
                        submission.getExecutionId(), result.getOverallStatus());
            }
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

package xyz.hrishabhjoshi.codeexecutionengine.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTerminalResult;
import xyz.hrishabhjoshi.codeexecutionengine.core.mapping.RunnerSubmissionStatusMapper;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.V2ClaimRecord;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;
import xyz.hrishabhjoshi.codeexecutionengine.runner.redis.RedisV2RunnerClient;
import xyz.hrishabhjoshi.codeexecutionengine.runner.sandbox.NsJailProcessLauncher;
import xyz.hrishabhjoshi.codeexecutionengine.runner.workspace.WorkspaceManager;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PersistentRunnerMain {

    private static final Logger log = LoggerFactory.getLogger(PersistentRunnerMain.class);

    public static void main(String[] args) throws Exception {
        RunnerConfig config = RunnerConfig.fromEnvironment();
        AtomicBoolean running = new AtomicBoolean(true);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> running.set(false)));

        try (RedisV2RunnerClient redis = new RedisV2RunnerClient(config.redisUri(), config.keys())) {
            WorkspaceManager workspaces = new WorkspaceManager(config.workspaceRoot(), config.quarantineRoot());
            NsJailProcessLauncher sandbox = new NsJailProcessLauncher(config.nsjailBinary(), config.maxOutputBytes());
            TaskExecutionPipeline pipeline = new TaskExecutionPipeline(sandbox, TaskExecutionPipeline.defaultRuntimeSnapshot());
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

            log.info("Persistent runner {} starting (concurrency=1)", config.runnerId());
            while (running.get()) {
                redis.publishHeartbeat(config.runnerId(), "READY", config.leaseDuration().getSeconds());
                var claimedId = redis.blockClaimReady(config.blockTimeout().getSeconds());
                if (claimedId.isEmpty()) {
                    continue;
                }
                String executionId = claimedId.get();
                RunnerCrashTestHooks.syncpoint("AFTER_POP_TO_PROCESSING");
                String taskJson = redis.getTaskJson(executionId);
                if (taskJson == null || taskJson.isBlank()) {
                    log.warn("Missing task payload for {}", executionId);
                    continue;
                }
                RunnerTaskEnvelope envelope = mapper.readValue(taskJson, RunnerTaskEnvelope.class);
                String attemptToken = UUID.randomUUID().toString();
                String processingStatus = mapper.writeValueAsString(SubmissionStatusDto.builder()
                        .submissionId(envelope.submissionId())
                        .executionId(executionId)
                        .status("PROCESSING")
                        .startedAt(System.currentTimeMillis())
                        .workerId(config.runnerId())
                        .build());
                if (!redis.finalizeClaim(
                        executionId,
                        envelope.submissionId(),
                        config.runnerId(),
                        attemptToken,
                        1L,
                        processingStatus,
                        config.leaseDuration().getSeconds())) {
                    log.warn("Claim finalize failed for {}", executionId);
                    continue;
                }
                V2ClaimRecord claim = new V2ClaimRecord(config.runnerId(), attemptToken, 1L,
                        java.time.Instant.now().getEpochSecond() + config.leaseDuration().getSeconds());
                RunnerCrashTestHooks.syncpoint("AFTER_CLAIM_FINALIZE");
                Path workspace = null;
                boolean taskHandled = false;
                try {
                    workspace = workspaces.createTaskWorkspace();
                    redis.heartbeat(executionId, claim, config.leaseDuration().getSeconds());
                    RunnerTerminalResult terminal = pipeline.execute(envelope, workspace);
                    RunnerCrashTestHooks.syncpoint("AFTER_EXECUTE_BEFORE_COMPLETE");
                    String resultJson = mapper.writeValueAsString(terminal);
                    String statusJson = mapper.writeValueAsString(RunnerSubmissionStatusMapper.terminal(
                            terminal.result(), config.runnerId(), System.currentTimeMillis()));
                    RedisV2RunnerClient.CommitResult commit = redis.complete(
                            executionId,
                            envelope.submissionId(),
                            attemptToken,
                            resultJson,
                            statusJson,
                            config.leaseDuration().getSeconds());
                    if (commit == RedisV2RunnerClient.CommitResult.STALE) {
                        log.warn("Stale commit ignored for {}", executionId);
                    }
                    taskHandled = true;
                } finally {
                    workspaces.deleteOrQuarantine(workspace);
                }
                if (RunnerCrashTestHooks.singleTaskMode() && taskHandled) {
                    running.set(false);
                }
            }
        }
    }
}

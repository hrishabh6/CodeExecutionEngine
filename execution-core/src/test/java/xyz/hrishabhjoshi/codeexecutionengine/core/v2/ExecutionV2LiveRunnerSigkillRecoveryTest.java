package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import xyz.hrishabhjoshi.codeexecutionengine.core.codec.ExecutionPayloadCodecCore;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.ExecutionContractVersion;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.support.NsJailBinaryLocator;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.support.RedisV2TestBase;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.support.RunnerInstallLocator;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ExecutionV2LiveRunnerSigkillRecoveryTest extends RedisV2TestBase {

    enum CrashSyncpoint {
        AFTER_POP_TO_PROCESSING,
        AFTER_CLAIM_FINALIZE,
        AFTER_EXECUTE_BEFORE_COMPLETE
    }

    @ParameterizedTest
    @EnumSource(CrashSyncpoint.class)
    void sigkillAtTransitionRecoversWithSingleTerminalResult(CrashSyncpoint syncpoint) throws Exception {
        assumeTrue(NsJailBinaryLocator.resolve().isPresent(), "NsJail binary required for live runner gate");
        assumeTrue(RunnerInstallLocator.resolve().isPresent(), "Run :nsjail-runner:installDist first");

        String executionId = "exec-sigkill-" + syncpoint.name().toLowerCase() + "-" + UUID.randomUUID();
        String submissionId = "sub-sigkill-" + UUID.randomUUID();
        enqueueAddTask(executionId, submissionId);

        Path workspaceRoot = Files.createTempDirectory("cxe-runner-ws-");
        Path quarantineRoot = Files.createTempDirectory("cxe-runner-q-");
        Path marker = Files.createTempFile("cxe-runner-marker-", ".txt");

        Process crashed = startRunner(
                syncpoint.name(),
                marker,
                workspaceRoot,
                quarantineRoot,
                false);
        waitForMarker(marker, Duration.ofSeconds(90));
        assertNull(client.get(keys.resultKey(executionId)), "terminal must not exist before recovery");

        crashed.destroyForcibly();
        assertTrue(crashed.waitFor(15, TimeUnit.SECONDS));

        prepareRedisForRecovery(syncpoint, executionId);
        assertNull(client.get(keys.resultKey(executionId)), "terminal still absent before recovery runner");

        Process recovery = startRunner(null, null, workspaceRoot, quarantineRoot, true);
        String terminal = waitForTerminalResult(executionId, Duration.ofSeconds(120));
        assertNotNull(terminal);
        assertNull(client.get(keys.taskKey(executionId)));
        assertEquals(0L, client.processingLength());
        assertEquals(0L, client.readyLength());

        String secondRead = client.get(keys.resultKey(executionId));
        assertEquals(terminal, secondRead, "terminal result must be stable (idempotent read)");

        recovery.destroyForcibly();
        recovery.waitFor(10, TimeUnit.SECONDS);
    }

    private void enqueueAddTask(String executionId, String submissionId) throws Exception {
        CodeSubmissionDTO submission = addSubmission(executionId, submissionId);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ExecutionPayloadCodecCore codec = new ExecutionPayloadCodecCore(mapper);
        String payload = codec.encode(submission);
        RunnerTaskEnvelope envelope = new RunnerTaskEnvelope(
                ExecutionContractVersion.V2_RUNNER_QUEUE,
                executionId,
                "0",
                submissionId,
                executionId,
                "RUN",
                "java",
                payload,
                Instant.now().plusSeconds(3600),
                "default");
        String taskJson = mapper.writeValueAsString(envelope);
        String statusJson = statusJson(submissionId, executionId, "QUEUED");
        String hash = UUID.randomUUID().toString().replace("-", "");
        List<Object> enqueued = client.enqueue(executionId, taskJson, statusJson, hash, 32, 3600, 3600);
        assertEquals(1L, Long.parseLong(String.valueOf(enqueued.get(0))));
    }

    private void prepareRedisForRecovery(CrashSyncpoint syncpoint, String executionId) {
        if (syncpoint == CrashSyncpoint.AFTER_POP_TO_PROCESSING) {
            client.simulateReaper(Instant.now().getEpochSecond());
            return;
        }
        String claimRaw = client.get(keys.claimKey(executionId));
        if (claimRaw != null) {
            V2ClaimRecord claim = V2ClaimRecord.decode(claimRaw);
            V2ClaimRecord expired = new V2ClaimRecord(
                    claim.runnerId(), claim.attemptToken(), claim.attemptGeneration(),
                    Instant.now().getEpochSecond() - 30);
            client.setClaim(executionId, expired.encode(), 3600);
            client.simulateReaper(Instant.now().getEpochSecond());
        }
    }

    private Process startRunner(
            String syncpoint,
            Path marker,
            Path workspaceRoot,
            Path quarantineRoot,
            boolean singleTask) throws Exception {
        Path runnerBin = RunnerInstallLocator.resolve().orElseThrow();
        Path nsjail = Path.of(NsJailBinaryLocator.resolve().orElseThrow());
        Path scriptsLib = RunnerInstallLocator.repoRoot().resolve("../scripts/.local/lib").normalize();

        ProcessBuilder builder = new ProcessBuilder(runnerBin.toString());
        builder.environment().put("CXE_NSJAIL_BIN", nsjail.toString());
        builder.environment().put("CXE_REDIS_HOST", RedisV2TestBase.redisHost());
        builder.environment().put("CXE_REDIS_PORT", String.valueOf(RedisV2TestBase.redisPort()));
        builder.environment().put("CXE_V2_KEY_PREFIX", keys.prefix());
        builder.environment().put("CXE_RUNNER_ID", "runner-sigkill-test");
        builder.environment().put("CXE_RUNNER_BLOCK_TIMEOUT_SECONDS", "2");
        builder.environment().put("CXE_RUNNER_LEASE_SECONDS", "120");
        builder.environment().put("CXE_RUNNER_WORKSPACE_ROOT", workspaceRoot.toString());
        builder.environment().put("CXE_RUNNER_QUARANTINE_ROOT", quarantineRoot.toString());
        if (syncpoint != null) {
            builder.environment().put("CXE_RUNNER_CRASH_TEST_SYNCPOINT", syncpoint);
            builder.environment().put("CXE_RUNNER_CRASH_TEST_MARKER", marker.toString());
        }
        if (singleTask) {
            builder.environment().put("CXE_RUNNER_SINGLE_TASK", "true");
        }
        if (Files.isDirectory(scriptsLib)) {
            String existing = builder.environment().getOrDefault("LD_LIBRARY_PATH", "");
            builder.environment().put("LD_LIBRARY_PATH", scriptsLib + (existing.isBlank() ? "" : ":" + existing));
        }
        String javaHome = System.getProperty("java.home");
        if (javaHome != null && !javaHome.isBlank()) {
            builder.environment().put("JAVA_HOME", javaHome);
        }
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    private static void waitForMarker(Path marker, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(marker)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for runner syncpoint marker: " + marker);
    }

    private String waitForTerminalResult(String executionId, Duration timeout) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            String result = client.get(keys.resultKey(executionId));
            if (result != null && !result.isBlank()) {
                return result;
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Timed out waiting for terminal result for " + executionId);
    }

    private static CodeSubmissionDTO addSubmission(String executionId, String submissionId) {
        CodeSubmissionDTO.QuestionMetadata metadata = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName("q.add")
                .functionName("add")
                .returnType("int")
                .questionType("ALGORITHM")
                .parameters(List.of(new ParamInfoDTO("a", "int"), new ParamInfoDTO("b", "int")))
                .build();
        return CodeSubmissionDTO.builder()
                .submissionId(submissionId)
                .executionId(executionId)
                .language("java")
                .userSolutionCode("class Solution { public int add(int a, int b) { return a + b; } }")
                .questionMetadata(metadata)
                .testCases(List.of(Map.of("input", Map.of("a", 2, "b", 3))))
                .build();
    }
}

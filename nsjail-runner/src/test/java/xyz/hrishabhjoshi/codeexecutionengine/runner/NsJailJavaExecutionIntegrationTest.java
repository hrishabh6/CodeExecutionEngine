package xyz.hrishabhjoshi.codeexecutionengine.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.hrishabhjoshi.codeexecutionengine.core.codec.ExecutionPayloadCodecCore;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTerminalResult;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.runner.sandbox.NsJailProcessLauncher;
import xyz.hrishabhjoshi.codeexecutionengine.runner.support.NsJailTestSupport;
import xyz.hrishabhjoshi.codeexecutionengine.runner.support.RunnerGoldenFixtures;
import xyz.hrishabhjoshi.codeexecutionengine.runner.workspace.WorkspaceManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NsJailJavaExecutionIntegrationTest {

    private static String nsjailBinary;

    @BeforeAll
    static void requireNsJail() {
        Optional<String> resolved = NsJailTestSupport.resolveNsJailBinary();
        assumeTrue(resolved.isPresent(), "NsJail binary unavailable (set CXE_NSJAIL_BIN or build scripts/.local/bin/nsjail)");
        nsjailBinary = resolved.get();
    }

    @Test
    void compilesAndRunsJavaThroughNsJail(@TempDir Path tempRoot) throws Exception {
        CodeSubmissionDTO submission = RunnerGoldenFixtures.javaAddSubmission();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ExecutionPayloadCodecCore codec = new ExecutionPayloadCodecCore(mapper);
        RunnerTaskEnvelope envelope = RunnerGoldenFixtures.envelopeFor(submission, codec.encode(submission));

        WorkspaceManager workspaces = new WorkspaceManager(tempRoot.resolve("ws"), tempRoot.resolve("q"));
        Path workspace = workspaces.createTaskWorkspace();
        try {
            NsJailProcessLauncher sandbox = new NsJailProcessLauncher(nsjailBinary, 262144);
            TaskExecutionPipeline pipeline = new TaskExecutionPipeline(sandbox, TaskExecutionPipeline.defaultRuntimeSnapshot());
            RunnerTerminalResult terminal = pipeline.execute(envelope, workspace);

            assertEquals(
                    Status.SUCCESS,
                    terminal.result().getOverallStatus(),
                    () -> "compilation/run detail: " + terminal.result().getCompilationOutput());
            assertEquals(1, terminal.result().getTestCaseOutputs().size());
            assertEquals("5", terminal.result().getTestCaseOutputs().getFirst().getActualOutput());
        } finally {
            workspaces.deleteOrQuarantine(workspace);
            assertFalse(Files.exists(workspace));
        }
    }

    @Test
    void compilationErrorSurfacesAsCompilationStatus(@TempDir Path tempRoot) throws Exception {
        CodeSubmissionDTO submission = RunnerGoldenFixtures.javaCompileErrorSubmission();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ExecutionPayloadCodecCore codec = new ExecutionPayloadCodecCore(mapper);
        RunnerTaskEnvelope envelope = RunnerGoldenFixtures.envelopeFor(submission, codec.encode(submission));

        WorkspaceManager workspaces = new WorkspaceManager(tempRoot.resolve("ws"), tempRoot.resolve("q"));
        Path workspace = workspaces.createTaskWorkspace();
        try {
            NsJailProcessLauncher sandbox = new NsJailProcessLauncher(nsjailBinary, 262144);
            TaskExecutionPipeline pipeline = new TaskExecutionPipeline(sandbox, TaskExecutionPipeline.defaultRuntimeSnapshot());
            RunnerTerminalResult terminal = pipeline.execute(envelope, workspace);
            assertEquals(Status.COMPILATON_ERROR, terminal.result().getOverallStatus());
        } finally {
            workspaces.deleteOrQuarantine(workspace);
        }
    }

    @Test
    void sandboxEnforcesTimeLimit(@TempDir Path tempRoot) throws Exception {
        NsJailProcessLauncher sandbox = new NsJailProcessLauncher(nsjailBinary, 262144);
        Path workspace = tempRoot.resolve("timeout-ws");
        Files.createDirectories(workspace);
        NsJailProcessLauncher.SandboxStageResult result =
                sandbox.runStage(workspace, java.util.List.of("sleep 10"), 1L);
        assertTrue(
                result.timedOut() || result.exitCode() == 124 || result.exitCode() == 137,
                () -> "expected timeout signal, exit=" + result.exitCode() + " output=" + result.output());
    }
}

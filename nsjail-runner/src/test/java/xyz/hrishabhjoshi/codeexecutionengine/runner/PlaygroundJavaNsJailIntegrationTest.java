package xyz.hrishabhjoshi.codeexecutionengine.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.ExecutionContractVersion;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.PlaygroundTaskPayload;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTerminalResult;
import xyz.hrishabhjoshi.codeexecutionengine.core.mapping.RunnerSubmissionStatusMapper;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.dto.SubmissionStatusDto;
import xyz.hrishabhjoshi.codeexecutionengine.runner.sandbox.NsJailProcessLauncher;
import xyz.hrishabhjoshi.codeexecutionengine.runner.support.NsJailTestSupport;
import xyz.hrishabhjoshi.codeexecutionengine.runner.workspace.WorkspaceManager;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PlaygroundJavaNsJailIntegrationTest {

    private static String nsjailBinary;
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @BeforeAll
    static void requireNsJail() {
        Optional<String> resolved = NsJailTestSupport.resolveNsJailBinary();
        assumeTrue(resolved.isPresent(), "NsJail binary unavailable");
        nsjailBinary = resolved.get();
    }

    @Test
    void javaPlaygroundCapturesStdinStdoutAndStderr(@TempDir Path tempRoot) throws Exception {
        String source = """
                public class Main {
                  public static void main(String[] args) throws Exception {
                    System.out.print(new String(System.in.readAllBytes()));
                    System.err.print("err-side");
                  }
                }
                """;
        SubmissionStatusDto status = run(tempRoot, source, "hello");
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("SUCCESS", status.getRawExecutionStatus(), status.getCompilationOutput());
        assertEquals("hello", status.getStdout());
        assertEquals("err-side", status.getStderr());
        assertEquals(0, status.getExitCode().intValue());
    }

    @Test
    void javaPlaygroundCompilationError(@TempDir Path tempRoot) throws Exception {
        SubmissionStatusDto status = run(tempRoot, "public class Main { int ", "");
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("COMPILE_ERROR", status.getRawExecutionStatus());
        assertTrue(status.getCompilationOutput() != null && !status.getCompilationOutput().isBlank());
    }

    @Test
    void javaPlaygroundRuntimeError(@TempDir Path tempRoot) throws Exception {
        String source = """
                public class Main {
                  public static void main(String[] args) {
                    throw new RuntimeException("boom");
                  }
                }
                """;
        SubmissionStatusDto status = run(tempRoot, source, "");
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("RUNTIME_ERROR", status.getRawExecutionStatus());
        assertTrue(status.getStderr().contains("boom"));
    }

    @Test
    void javaPlaygroundTimeout(@TempDir Path tempRoot) throws Exception {
        String source = """
                public class Main {
                  public static void main(String[] args) {
                    while (true) {}
                  }
                }
                """;
        SubmissionStatusDto status = run(tempRoot, source, "");
        assertEquals("COMPLETED", status.getStatus());
        assertEquals("TIME_LIMIT_EXCEEDED", status.getRawExecutionStatus());
    }

    @Test
    void pythonPlaygroundIsNotExecuted(@TempDir Path tempRoot) throws Exception {
        RunnerTerminalResult terminal = execute(tempRoot, "python", "print(1)", "");
        assertEquals(Status.UNKNOWN, terminal.result().getOverallStatus());
    }

    private static SubmissionStatusDto run(Path tempRoot, String source, String stdin) throws Exception {
        RunnerTerminalResult terminal = execute(tempRoot, "java", source, stdin);
        return RunnerSubmissionStatusMapper.playground(terminal.result(), "test", 1L);
    }

    private static RunnerTerminalResult execute(Path tempRoot, String language, String source, String stdin)
            throws Exception {
        String payload = Base64.getEncoder().encodeToString(
                MAPPER.writeValueAsBytes(new PlaygroundTaskPayload(language, source, stdin)));
        RunnerTaskEnvelope envelope = new RunnerTaskEnvelope(
                ExecutionContractVersion.V2_RUNNER_QUEUE,
                "task",
                "0",
                "playground-1",
                "playground-1",
                "PLAYGROUND",
                language,
                payload,
                Instant.now().plusSeconds(120),
                "default");
        WorkspaceManager workspaces = new WorkspaceManager(tempRoot.resolve("ws"), tempRoot.resolve("q"));
        Path workspace = workspaces.createTaskWorkspace();
        NsJailProcessLauncher sandbox = new NsJailProcessLauncher(nsjailBinary, 262144);
        TaskExecutionPipeline pipeline = new TaskExecutionPipeline(
                sandbox, TaskExecutionPipeline.defaultRuntimeSnapshot());
        return pipeline.execute(envelope, workspace);
    }
}

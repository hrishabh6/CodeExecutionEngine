package xyz.hrishabhjoshi.codeexecutionengine.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.PlaygroundTaskPayload;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTerminalResult;
import xyz.hrishabhjoshi.codeexecutionengine.core.runtime.ExecutionRuntimeSnapshot;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.V2TerminalCategory;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.runner.sandbox.NsJailProcessLauncher;
import xyz.hrishabhjoshi.codeexecutionengine.runner.sandbox.NsJailProcessLauncher.SandboxStageResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Runs a Java playground snippet inside NsJail. No question metadata and no oracle.
 * Python and other languages are not executed here; CXE keeps them on Kubernetes Jobs.
 */
public final class PlaygroundNsJailExecutor {

    /** Matches execution.playground.execution-timeout-seconds default. */
    static final long RUN_TIMEOUT_SECONDS = 5;

    /** Same limits as the submission runner, passed on the command so they do not land in stderr. */
    private static final String JAVA_RUNTIME_FLAGS =
            "-Xmx256m -XX:MaxMetaspaceSize=128m -XX:CompressedClassSpaceSize=64m "
                    + "-XX:ReservedCodeCacheSize=32m -XX:ActiveProcessorCount=2";

    private final ObjectMapper objectMapper;
    private final NsJailProcessLauncher sandbox;
    private final ExecutionRuntimeSnapshot runtime;

    public PlaygroundNsJailExecutor(
            ObjectMapper objectMapper,
            NsJailProcessLauncher sandbox,
            ExecutionRuntimeSnapshot runtime) {
        this.objectMapper = objectMapper;
        this.sandbox = sandbox;
        this.runtime = runtime;
    }

    public RunnerTerminalResult execute(RunnerTaskEnvelope envelope, Path workspace) {
        try {
            byte[] decoded = Base64.getDecoder().decode(envelope.payloadBase64());
            PlaygroundTaskPayload payload = objectMapper.readValue(decoded, PlaygroundTaskPayload.class);
            String language = payload.language() == null ? "" : payload.language().trim().toLowerCase(Locale.ROOT);
            String source = payload.sourceCode() == null ? "" : payload.sourceCode();
            String stdin = payload.stdin() == null ? "" : payload.stdin();
            Files.writeString(workspace.resolve("stdin.txt"), stdin, StandardCharsets.UTF_8);
            long compileTimeout = Math.max(1, runtime.compilationTimeoutSeconds());
            if ("java".equals(language)) {
                return runJava(envelope, workspace, source, compileTimeout);
            }
            return terminal(envelope, Status.UNKNOWN, V2TerminalCategory.CONTRACT_UNSUPPORTED,
                    "Playground NsJail accepts Java only", "", "", 1, 0, false);
        } catch (Exception e) {
            return terminal(envelope, Status.INTERNAL_ERROR, V2TerminalCategory.INFRASTRUCTURE_ERROR,
                    e.getMessage(), "", "", 1, 0, false);
        }
    }

    private RunnerTerminalResult runJava(RunnerTaskEnvelope envelope, Path workspace, String source, long timeout)
            throws Exception {
        Files.writeString(workspace.resolve("Main.java"), source, StandardCharsets.UTF_8);
        String javac = runtime.requireLanguage("java").compileCommand();
        SandboxStageResult compile = sandbox.runStage(
                workspace,
                List.of(TaskExecutionPipeline.javaToolOptionsPrefix() + javac + " /workspace/Main.java"),
                timeout,
                "");
        if (compile.timedOut()) {
            return terminal(envelope, Status.TIMEOUT, V2TerminalCategory.TIME_LIMIT_EXCEEDED,
                    compile.output(), "", "", 124, timeout * 1000, compile.truncated());
        }
        if (compile.exitCode() != 0) {
            return terminal(envelope, Status.COMPILATON_ERROR, V2TerminalCategory.COMPILATION_ERROR,
                    compile.output(), "", "", compile.exitCode(), 0, compile.truncated());
        }
        String java = runtime.requireLanguage("java").runCommand();
        long started = System.currentTimeMillis();
        SandboxStageResult run = sandbox.runStage(
                workspace,
                List.of(java + " " + JAVA_RUNTIME_FLAGS
                        + " -cp /workspace Main < /workspace/stdin.txt"
                        + " > /workspace/stdout.txt 2> /workspace/stderr.txt"),
                RUN_TIMEOUT_SECONDS,
                "");
        String stdout = readText(workspace.resolve("stdout.txt"));
        String stderr = readText(workspace.resolve("stderr.txt"));
        return mapRun(envelope, compile.output(), run, stdout, stderr, System.currentTimeMillis() - started);
    }

    private static String readText(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path) : "";
        } catch (Exception e) {
            return "";
        }
    }

    private RunnerTerminalResult mapRun(
            RunnerTaskEnvelope envelope,
            String compilerOutput,
            SandboxStageResult run,
            String stdout,
            String stderr,
            long runtimeMs) {
        if (run.timedOut()) {
            return terminal(envelope, Status.TIMEOUT, V2TerminalCategory.TIME_LIMIT_EXCEEDED,
                    compilerOutput, stdout, stderr, 124, runtimeMs, run.truncated());
        }
        if (run.exitCode() != 0) {
            return terminal(envelope, Status.RUNTIME_ERROR, V2TerminalCategory.RUNTIME_ERROR,
                    compilerOutput, stdout, stderr, run.exitCode(), runtimeMs, run.truncated());
        }
        return terminal(envelope, Status.SUCCESS, V2TerminalCategory.SUCCESS,
                compilerOutput, stdout, stderr, run.exitCode(), runtimeMs, run.truncated());
    }

    private static RunnerTerminalResult terminal(
            RunnerTaskEnvelope envelope,
            Status status,
            V2TerminalCategory category,
            String compilerOutput,
            String stdout,
            String stderr,
            int exitCode,
            long runtimeMs,
            boolean truncated) {
        CodeExecutionResultDTO dto = CodeExecutionResultDTO.builder()
                .submissionId(envelope.submissionId())
                .executionId(envelope.executionId())
                .overallStatus(status)
                .compilationOutput(compilerOutput == null ? "" : compilerOutput)
                .testCaseOutputs(List.of(CodeExecutionResultDTO.TestCaseOutput.builder()
                        .testCaseIndex(exitCode)
                        .actualOutput(stdout == null ? "" : stdout)
                        .errorMessage(stderr == null ? "" : stderr)
                        .executionTimeMs(runtimeMs)
                        .build()))
                .build();
        if (truncated && dto.getCompilationOutput().isEmpty()) {
            dto.setCompilationOutput("output truncated");
        }
        return new RunnerTerminalResult(
                envelope.contractVersion(),
                envelope.taskId(),
                envelope.attemptId(),
                category.name(),
                dto);
    }
}

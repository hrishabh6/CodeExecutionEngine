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
 * Runs a playground snippet inside NsJail. No question metadata and no oracle.
 * Java and Python match the existing local playground executor. Other languages
 * stay on the Kubernetes Job path until a sandbox command exists for them.
 */
public final class PlaygroundNsJailExecutor {

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
            long timeout = Math.max(1, runtime.compilationTimeoutSeconds());
            return switch (language) {
                case "java" -> runJava(envelope, workspace, source, timeout);
                case "python" -> runPython(envelope, workspace, source, timeout);
                default -> terminal(envelope, Status.UNKNOWN, V2TerminalCategory.CONTRACT_UNSUPPORTED,
                        "Unsupported playground language", "", "", 1, 0, false);
            };
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
                List.of(TaskExecutionPipeline.javaToolOptionsPrefix()
                        + java + " -cp /workspace Main < /workspace/stdin.txt"),
                timeout,
                "");
        return mapRun(envelope, compile.output(), run, System.currentTimeMillis() - started);
    }

    private RunnerTerminalResult runPython(RunnerTaskEnvelope envelope, Path workspace, String source, long timeout)
            throws Exception {
        Files.writeString(workspace.resolve("main.py"), source, StandardCharsets.UTF_8);
        String python = runtime.requireLanguage("python").runCommand();
        long started = System.currentTimeMillis();
        SandboxStageResult run = sandbox.runStage(
                workspace,
                List.of(python + " /workspace/main.py < /workspace/stdin.txt"),
                timeout,
                "");
        return mapRun(envelope, "", run, System.currentTimeMillis() - started);
    }

    private RunnerTerminalResult mapRun(
            RunnerTaskEnvelope envelope,
            String compilerOutput,
            SandboxStageResult run,
            long runtimeMs) {
        if (run.timedOut()) {
            return terminal(envelope, Status.TIMEOUT, V2TerminalCategory.TIME_LIMIT_EXCEEDED,
                    compilerOutput, run.output(), "", 124, runtimeMs, run.truncated());
        }
        if (run.exitCode() != 0) {
            return terminal(envelope, Status.RUNTIME_ERROR, V2TerminalCategory.RUNTIME_ERROR,
                    compilerOutput, "", run.output(), run.exitCode(), runtimeMs, run.truncated());
        }
        return terminal(envelope, Status.SUCCESS, V2TerminalCategory.SUCCESS,
                compilerOutput, run.output(), "", run.exitCode(), runtimeMs, run.truncated());
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

package xyz.hrishabhjoshi.codeexecutionengine.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import xyz.hrishabhjoshi.codeexecutionengine.core.codec.ExecutionPayloadCodecCore;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTerminalResult;
import xyz.hrishabhjoshi.codeexecutionengine.core.generation.SourceWorkspaceGenerator;
import xyz.hrishabhjoshi.codeexecutionengine.core.pipeline.CompileCommandPlanner;
import xyz.hrishabhjoshi.codeexecutionengine.core.pipeline.ExecutionOutputParser;
import xyz.hrishabhjoshi.codeexecutionengine.core.pipeline.RunCommandPlanner;
import xyz.hrishabhjoshi.codeexecutionengine.config.RunnerClasspath;
import xyz.hrishabhjoshi.codeexecutionengine.core.runtime.ExecutionRuntimeSnapshot;
import xyz.hrishabhjoshi.codeexecutionengine.core.runtime.LanguageRuntimeSpec;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.V2TerminalCategory;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeExecutionResultDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;
import xyz.hrishabhjoshi.codeexecutionengine.dto.Status;
import xyz.hrishabhjoshi.codeexecutionengine.runner.sandbox.NsJailProcessLauncher;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class TaskExecutionPipeline {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ExecutionPayloadCodecCore payloadCodec = new ExecutionPayloadCodecCore(objectMapper);
    private final SourceWorkspaceGenerator sourceGenerator = SourceWorkspaceGenerator.withDefaultRegistry();
    private final NsJailProcessLauncher sandbox;
    private final ExecutionRuntimeSnapshot runtime;

    public TaskExecutionPipeline(NsJailProcessLauncher sandbox, ExecutionRuntimeSnapshot runtime) {
        this.sandbox = sandbox;
        this.runtime = runtime;
    }

    public RunnerTerminalResult execute(RunnerTaskEnvelope envelope, Path workspace) {
        if (envelope.mode() != null && "PLAYGROUND".equalsIgnoreCase(envelope.mode())) {
            return new PlaygroundNsJailExecutor(objectMapper, sandbox, runtime).execute(envelope, workspace);
        }
        try {
            CodeSubmissionDTO submission = payloadCodec.decode(envelope.payloadBase64());
            sourceGenerator.generateIntoWorkspace(submission, workspace);
            String mainClass = submission.getQuestionMetadata().getFullyQualifiedPackageName() + ".Main";

            if ("java".equalsIgnoreCase(submission.getLanguage())) {
                return executeJava(envelope, submission, workspace, mainClass);
            }
            if ("python".equalsIgnoreCase(submission.getLanguage())) {
                return executePython(envelope, submission, workspace, mainClass);
            }
            return terminal(envelope, Status.UNKNOWN, V2TerminalCategory.CONTRACT_UNSUPPORTED,
                    "Unsupported language");
        } catch (Exception e) {
            return terminal(envelope, Status.INTERNAL_ERROR, V2TerminalCategory.INFRASTRUCTURE_ERROR,
                    e.getMessage());
        }
    }

    private RunnerTerminalResult executeJava(
            RunnerTaskEnvelope envelope,
            CodeSubmissionDTO submission,
            Path workspace,
            String mainClass) throws Exception {
        CompileCommandPlanner.JavaCompilePlan plan = CompileCommandPlanner.planJavaCompile(runtime, workspace);
        if (plan.sourceFiles().isEmpty()) {
            return terminal(envelope, Status.COMPILATON_ERROR, V2TerminalCategory.COMPILATION_ERROR,
                    "No sources");
        }
        List<String> compileCommand = rewriteForSandboxWorkspace(plan.command(), workspace);
        NsJailProcessLauncher.SandboxStageResult compile = sandbox.runStage(
                workspace,
                List.of(javaToolOptionsPrefix() + String.join(" ", compileCommand)),
                runtime.compilationTimeoutSeconds(),
                runtime.resolvedExtraClasspath());
        if (compile.timedOut()) {
            return terminal(envelope, Status.TIMEOUT, V2TerminalCategory.TIME_LIMIT_EXCEEDED,
                    compile.output());
        }
        if (compile.exitCode() != 0) {
            return terminal(envelope, Status.COMPILATON_ERROR, V2TerminalCategory.COMPILATION_ERROR,
                    compile.output());
        }

        List<String> runCmd = rewriteForSandboxWorkspace(
                RunCommandPlanner.planJavaRun(runtime, workspace, mainClass), workspace);
        NsJailProcessLauncher.SandboxStageResult run = sandbox.runStage(
                workspace,
                List.of(javaToolOptionsPrefix() + String.join(" ", runCmd)),
                10,
                runtime.resolvedExtraClasspath());
        if (run.timedOut()) {
            return terminal(envelope, Status.TIMEOUT, V2TerminalCategory.TIME_LIMIT_EXCEEDED,
                    run.output());
        }
        List<ExecutionResult.TestCaseOutput> outputs =
                ExecutionOutputParser.parseHarnessOutput(run.output(), null, null);
        CodeExecutionResultDTO dto = CodeExecutionResultDTO.builder()
                .submissionId(envelope.submissionId())
                .executionId(envelope.executionId())
                .overallStatus(run.exitCode() == 0 ? Status.SUCCESS : Status.RUNTIME_ERROR)
                .testCaseOutputs(mapOutputs(outputs))
                .build();
        return new RunnerTerminalResult(
                envelope.contractVersion(),
                envelope.taskId(),
                envelope.attemptId(),
                V2TerminalCategory.SUCCESS.name(),
                dto);
    }

    private RunnerTerminalResult executePython(
            RunnerTaskEnvelope envelope,
            CodeSubmissionDTO submission,
            Path workspace,
            String mainClass) throws Exception {
        List<String> runCmd = RunCommandPlanner.planPythonRun(runtime, mainClass);
        NsJailProcessLauncher.SandboxStageResult run = sandbox.runStage(
                workspace, List.of(String.join(" ", runCmd)), 10, runtime.resolvedExtraClasspath());
        if (run.timedOut()) {
            return terminal(envelope, Status.TIMEOUT, V2TerminalCategory.TIME_LIMIT_EXCEEDED,
                    run.output());
        }
        List<ExecutionResult.TestCaseOutput> outputs =
                ExecutionOutputParser.parseHarnessOutput(run.output(), null, null);
        CodeExecutionResultDTO dto = CodeExecutionResultDTO.builder()
                .submissionId(envelope.submissionId())
                .executionId(envelope.executionId())
                .overallStatus(run.exitCode() == 0 ? Status.SUCCESS : Status.RUNTIME_ERROR)
                .testCaseOutputs(mapOutputs(outputs))
                .build();
        return new RunnerTerminalResult(
                envelope.contractVersion(),
                envelope.taskId(),
                envelope.attemptId(),
                V2TerminalCategory.SUCCESS.name(),
                dto);
    }

    private RunnerTerminalResult terminal(
            RunnerTaskEnvelope envelope,
            Status status,
            V2TerminalCategory category,
            String detail) {
        CodeExecutionResultDTO dto = CodeExecutionResultDTO.builder()
                .submissionId(envelope.submissionId())
                .executionId(envelope.executionId())
                .overallStatus(status)
                .compilationOutput(detail)
                .build();
        return new RunnerTerminalResult(
                envelope.contractVersion(),
                envelope.taskId(),
                envelope.attemptId(),
                category.name(),
                dto);
    }

    static String javaToolOptionsPrefix() {
        return "export JAVA_TOOL_OPTIONS='-Xmx256m -XX:MaxMetaspaceSize=128m -XX:CompressedClassSpaceSize=64m "
                + "-XX:ReservedCodeCacheSize=32m -XX:ActiveProcessorCount=2'; ";
    }

    private static List<String> rewriteForSandboxWorkspace(List<String> command, Path workspace) {
        String hostRoot = workspace.toAbsolutePath().normalize().toString();
        return command.stream()
                .map(token -> {
                    if (token.startsWith(hostRoot)) {
                        return "/workspace" + token.substring(hostRoot.length()).replace('\\', '/');
                    }
                    return token;
                })
                .toList();
    }

    private static List<CodeExecutionResultDTO.TestCaseOutput> mapOutputs(
            List<ExecutionResult.TestCaseOutput> outputs) {
        return outputs.stream()
                .map(o -> CodeExecutionResultDTO.TestCaseOutput.builder()
                        .testCaseIndex(o.getTestCaseIndex())
                        .actualOutput(o.getActualOutput())
                        .executionTimeMs(o.getExecutionTimeMs())
                        .errorMessage(o.getErrorMessage())
                        .errorType(o.getErrorType())
                        .memoryBytes(o.getMemoryBytes())
                        .build())
                .toList();
    }

    public static ExecutionRuntimeSnapshot defaultRuntimeSnapshot() {
        String javaHome = System.getProperty("java.home");
        LanguageRuntimeSpec java = new LanguageRuntimeSpec(
                javaHome + "/bin/javac",
                javaHome + "/bin/java");
        LanguageRuntimeSpec python = new LanguageRuntimeSpec("", "python3");
        String extraClasspath = RunnerClasspath.resolve(System.getenv(RunnerClasspath.ENV_NAME));
        return new ExecutionRuntimeSnapshot(30, extraClasspath, Map.of("java", java, "python", python));
    }
}

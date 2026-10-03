package xyz.hrishabhjoshi.codeexecutionengine.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.PlaygroundExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionRequest;
import xyz.hrishabhjoshi.codeexecutionengine.dto.RawExecutionResult;
import xyz.hrishabhjoshi.codeexecutionengine.dto.RawExecutionStatus;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.MemoryParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaygroundProgramExecutor {

    private final ManagedProcessRunner processRunner;
    private final ExecutionRuntimeProperties runtimeProperties;
    private final PlaygroundExecutionProperties playgroundProperties;
    private final PlaygroundJavaSourceValidator javaSourceValidator;

    public RawExecutionResult execute(ExecutionRequest request) {
        String language = request.getLanguage() == null
                ? ""
                : request.getLanguage().trim().toLowerCase(Locale.ROOT);
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("cxe-playground-");
            return switch (language) {
                case "java" -> runJava(request, workspace);
                case "python" -> runPython(request, workspace);
                default -> internalError("Unsupported language");
            };
        } catch (ExecutionRequestRejectedException e) {
            return RawExecutionResult.builder()
                    .status(RawExecutionStatus.INTERNAL_ERROR)
                    .stderr(e.getCode())
                    .runtimeMs(0)
                    .outputTruncated(false)
                    .build();
        } catch (Exception e) {
            log.warn("[PLAYGROUND] internal failure submissionId={}: {}", request.getSubmissionId(), e.getMessage());
            return internalError("Execution failed");
        } finally {
            if (workspace != null) {
                deleteQuietly(workspace);
            }
        }
    }

    private RawExecutionResult runJava(ExecutionRequest request, Path workspace) throws IOException, InterruptedException {
        javaSourceValidator.validate(request.getCode());
        Path sourceFile = workspace.resolve("Main.java");
        Files.writeString(sourceFile, request.getCode(), StandardCharsets.UTF_8);

        List<String> compileCmd = new ArrayList<>(runtimeProperties.getRequiredLanguageRuntime("java").compileCommandTokens());
        compileCmd.add(sourceFile.toAbsolutePath().toString());

        ManagedProcessRunner.SeparatedProcessExecutionResult compileResult = processRunner.runSeparated(
                compileCmd,
                workspace,
                null,
                runtimeProperties.getCompilationTimeoutSeconds(),
                playgroundProperties.getMaxCompilerOutputBytes(),
                true,
                "playground-javac");

        String compilerOutput = OutputByteLimiter.truncateUtf8(
                compileResult.stdout() + compileResult.stderr(),
                playgroundProperties.getMaxCompilerOutputBytes());

        if (compileResult.timedOut()) {
            return RawExecutionResult.builder()
                    .status(RawExecutionStatus.COMPILE_ERROR)
                    .compilerOutput(compilerOutput + "\nCompilation timed out.")
                    .runtimeMs((int) compileResult.wallClockMs())
                    .outputTruncated(compileResult.outputTruncated())
                    .build();
        }
        if (compileResult.exitCode() != 0) {
            return RawExecutionResult.builder()
                    .status(RawExecutionStatus.COMPILE_ERROR)
                    .compilerOutput(compilerOutput)
                    .runtimeMs((int) compileResult.wallClockMs())
                    .outputTruncated(compileResult.outputTruncated())
                    .build();
        }

        byte[] stdin = stdinBytes(request);
        List<String> runCmd = new ArrayList<>(runtimeProperties.getRequiredLanguageRuntime("java").runCommandTokens());
        runCmd.add("-cp");
        runCmd.add(workspace.toAbsolutePath().toString());
        runCmd.add("Main");

        ManagedProcessRunner.SeparatedProcessExecutionResult runResult = processRunner.runSeparated(
                runCmd,
                workspace,
                stdin,
                playgroundProperties.getExecutionTimeoutSeconds(),
                playgroundProperties.getMaxCombinedOutputBytes(),
                true,
                "playground-java");

        return mapRunResult(runResult);
    }

    private RawExecutionResult runPython(ExecutionRequest request, Path workspace) throws IOException, InterruptedException {
        Path script = workspace.resolve("main.py");
        Files.writeString(script, request.getCode(), StandardCharsets.UTF_8);

        List<String> pyCompile = new ArrayList<>(runtimeProperties.getRequiredLanguageRuntime("python").runCommandTokens());
        pyCompile.add("-m");
        pyCompile.add("py_compile");
        pyCompile.add(script.getFileName().toString());

        ManagedProcessRunner.SeparatedProcessExecutionResult compileResult = processRunner.runSeparated(
                pyCompile,
                workspace,
                null,
                runtimeProperties.getCompilationTimeoutSeconds(),
                playgroundProperties.getMaxCompilerOutputBytes(),
                false,
                "playground-py-compile");

        String compilerOutput = OutputByteLimiter.truncateUtf8(
                compileResult.stdout() + compileResult.stderr(),
                playgroundProperties.getMaxCompilerOutputBytes());

        if (compileResult.timedOut()) {
            return RawExecutionResult.builder()
                    .status(RawExecutionStatus.COMPILE_ERROR)
                    .compilerOutput(compilerOutput + "\nSyntax check timed out.")
                    .runtimeMs((int) compileResult.wallClockMs())
                    .outputTruncated(compileResult.outputTruncated())
                    .build();
        }
        if (compileResult.exitCode() != 0) {
            return RawExecutionResult.builder()
                    .status(RawExecutionStatus.COMPILE_ERROR)
                    .compilerOutput(compilerOutput)
                    .runtimeMs((int) compileResult.wallClockMs())
                    .outputTruncated(compileResult.outputTruncated())
                    .build();
        }

        byte[] stdin = stdinBytes(request);
        List<String> runCmd = new ArrayList<>(runtimeProperties.getRequiredLanguageRuntime("python").runCommandTokens());
        runCmd.add(script.getFileName().toString());

        ManagedProcessRunner.SeparatedProcessExecutionResult runResult = processRunner.runSeparated(
                runCmd,
                workspace,
                stdin,
                playgroundProperties.getExecutionTimeoutSeconds(),
                playgroundProperties.getMaxCombinedOutputBytes(),
                false,
                "playground-python");

        return mapRunResult(runResult);
    }

    private RawExecutionResult mapRunResult(ManagedProcessRunner.SeparatedProcessExecutionResult runResult) {
        Integer memoryKb = runResult.peakMemoryBytes() > 0
                ? MemoryParser.bytesToKB(runResult.peakMemoryBytes())
                : null;

        if (runResult.timedOut()) {
            return RawExecutionResult.builder()
                    .status(RawExecutionStatus.TIME_LIMIT_EXCEEDED)
                    .stdout(runResult.stdout())
                    .stderr(runResult.stderr())
                    .runtimeMs((int) runResult.wallClockMs())
                    .memoryKb(memoryKb)
                    .exitCode(runResult.exitCode())
                    .outputTruncated(runResult.outputTruncated())
                    .build();
        }
        if (runResult.exitCode() != 0) {
            return RawExecutionResult.builder()
                    .status(RawExecutionStatus.RUNTIME_ERROR)
                    .stdout(runResult.stdout())
                    .stderr(runResult.stderr())
                    .runtimeMs((int) runResult.wallClockMs())
                    .memoryKb(memoryKb)
                    .exitCode(runResult.exitCode())
                    .outputTruncated(runResult.outputTruncated())
                    .build();
        }
        return RawExecutionResult.builder()
                .status(RawExecutionStatus.SUCCESS)
                .stdout(runResult.stdout())
                .stderr(runResult.stderr())
                .runtimeMs((int) runResult.wallClockMs())
                .memoryKb(memoryKb)
                .exitCode(0)
                .outputTruncated(runResult.outputTruncated())
                .build();
    }

    private byte[] stdinBytes(ExecutionRequest request) {
        String stdin = request.getStdin() == null ? "" : request.getStdin();
        return stdin.getBytes(StandardCharsets.UTF_8);
    }

    private RawExecutionResult internalError(String message) {
        return RawExecutionResult.builder()
                .status(RawExecutionStatus.INTERNAL_ERROR)
                .stderr(message)
                .runtimeMs(0)
                .outputTruncated(false)
                .build();
    }

    private void deleteQuietly(Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}

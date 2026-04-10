package xyz.hrishabhjoshi.codeexecutionengine.service.compilation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CompilationResult;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service("javaCompilationService")
@RequiredArgsConstructor
public class JavaCompilationService implements CompilationService {

    private final ManagedProcessRunner processRunner;
    private final ExecutionRuntimeProperties runtimeProperties;

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public CompilationResult compile(String submissionId, String fullyQualifiedPackageName, Path submissionPath,
                                     Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("COMPILE_SERVICE: Starting compilation for submission: " + submissionId);

        List<String> javaFiles = collectJavaFiles(submissionPath);
        if (javaFiles.isEmpty()) {
            return new CompilationResult(false, "No Java source files found in " + submissionPath);
        }

        List<String> command = new ArrayList<>();
        command.addAll(runtimeProperties.getRequiredLanguageRuntime("java").compileCommandTokens());
        command.add("-d");
        command.add(submissionPath.toAbsolutePath().toString());
        command.add("-cp");
        command.add(submissionPath.toAbsolutePath().toString());
        command.addAll(javaFiles);

        ManagedProcessRunner.ProcessExecutionResult result = processRunner.run(
                command,
                submissionPath,
                logConsumer,
                runtimeProperties.getCompilationTimeoutSeconds(),
                "COMPILE_SERVICE_LOG");

        String compilationOutput = result.output();

        if (result.timedOut()) {
            logConsumer.accept("COMPILE_SERVICE: Compilation timed out after " + runtimeProperties.getCompilationTimeoutSeconds() + " seconds.");
            return new CompilationResult(false, compilationOutput + "\nCompilation timed out.");
        }

        if (result.exitCode() != 0) {
            logConsumer.accept("COMPILE_SERVICE: Compilation failed with exit code: " + result.exitCode());
            return new CompilationResult(false, compilationOutput);
        }

        logConsumer.accept("COMPILE_SERVICE: Compilation successful.");
        return new CompilationResult(true, compilationOutput);
    }

    private List<String> collectJavaFiles(Path submissionPath) throws IOException {
        try (Stream<Path> walk = Files.walk(submissionPath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .map(path -> path.toAbsolutePath().toString())
                    .toList();
        }
    }
}

package xyz.hrishabhjoshi.codeexecutionengine.service.compilation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.core.pipeline.CompileCommandPlanner;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CompilationResult;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

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

        CompileCommandPlanner.JavaCompilePlan plan =
                CompileCommandPlanner.planJavaCompile(runtimeProperties.toSnapshot(), submissionPath);
        if (plan.sourceFiles().isEmpty()) {
            return new CompilationResult(false, "No Java source files found in " + submissionPath);
        }

        ManagedProcessRunner.ProcessExecutionResult result = processRunner.run(
                plan.command(),
                submissionPath,
                logConsumer,
                runtimeProperties.getCompilationTimeoutSeconds(),
                "COMPILE_SERVICE_LOG",
                true);

        String compilationOutput = result.output();

        if (result.timedOut()) {
            logConsumer.accept("COMPILE_SERVICE: Compilation timed out after "
                    + runtimeProperties.getCompilationTimeoutSeconds() + " seconds.");
            return new CompilationResult(false, compilationOutput + "\nCompilation timed out.");
        }

        if (result.exitCode() != 0) {
            logConsumer.accept("COMPILE_SERVICE: Compilation failed with exit code: " + result.exitCode());
            return new CompilationResult(false, compilationOutput);
        }

        logConsumer.accept("COMPILE_SERVICE: Compilation successful.");
        return new CompilationResult(true, compilationOutput);
    }
}

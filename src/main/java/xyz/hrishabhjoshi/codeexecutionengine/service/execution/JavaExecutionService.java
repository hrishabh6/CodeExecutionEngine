package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.core.pipeline.ExecutionOutputParser;
import xyz.hrishabhjoshi.codeexecutionengine.core.pipeline.RunCommandPlanner;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class JavaExecutionService implements ExecutionService {

    private final ManagedProcessRunner processRunner;
    private final ExecutionRuntimeProperties runtimeProperties;

    @org.springframework.beans.factory.annotation.Value("${execution.worker.timeout-seconds:10}")
    private long executionTimeoutSeconds;

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public ExecutionResult run(String submissionId, Path submissionPath, String fullyQualifiedMainClass,
                               Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("EXECUTION_SERVICE: Starting worker-pod execution for submission: " + submissionId);

        List<String> command = RunCommandPlanner.planJavaRun(
                runtimeProperties.toSnapshot(), submissionPath, fullyQualifiedMainClass);

        ManagedProcessRunner.ProcessExecutionResult result = processRunner.run(
                command,
                submissionPath,
                logConsumer,
                executionTimeoutSeconds,
                "EXECUTION_SERVICE_LOG",
                true);

        if (result.timedOut()) {
            logConsumer.accept("EXECUTION_SERVICE: Execution timed out after " + executionTimeoutSeconds + " seconds.");
        } else {
            logConsumer.accept("EXECUTION_SERVICE: Execution completed with exit code: " + result.exitCode());
        }

        Long peakMemory = result.peakMemoryBytes() > 0 ? result.peakMemoryBytes() : null;
        List<ExecutionResult.TestCaseOutput> testCaseOutputs = new ArrayList<>(
                ExecutionOutputParser.parseHarnessOutput(result.output(), peakMemory, logConsumer));

        return ExecutionResult.builder()
                .rawOutput(result.output())
                .testCaseOutputs(testCaseOutputs)
                .timedOut(result.timedOut())
                .exitCode(result.exitCode())
                .build();
    }
}

package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
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

        List<String> command = new ArrayList<>(runtimeProperties.getRequiredLanguageRuntime("java").runCommandTokens());
        command.add("-cp");
        command.add(submissionPath.toAbsolutePath().toString());
        command.add(fullyQualifiedMainClass);

        ManagedProcessRunner.ProcessExecutionResult result = processRunner.run(
                command,
                submissionPath,
                logConsumer,
                executionTimeoutSeconds,
                "EXECUTION_SERVICE_LOG");

        if (result.timedOut()) {
            logConsumer.accept("EXECUTION_SERVICE: Execution timed out after " + executionTimeoutSeconds + " seconds.");
        } else {
            logConsumer.accept("EXECUTION_SERVICE: Execution completed with exit code: " + result.exitCode());
        }

        List<ExecutionResult.TestCaseOutput> testCaseOutputs = new ArrayList<>();
        parseExecutionOutputForResults(result.output(), testCaseOutputs, null, logConsumer);

        return ExecutionResult.builder()
                .rawOutput(result.output())
                .testCaseOutputs(testCaseOutputs)
                .timedOut(result.timedOut())
                .exitCode(result.exitCode())
                .build();
    }

    private void parseExecutionOutputForResults(String output, List<ExecutionResult.TestCaseOutput> results,
                                                Long memoryBytes, Consumer<String> logConsumer) {
        String[] lines = output.split("\n");
        int parsedCount = 0;

        for (String line : lines) {
            if (line.startsWith("TEST_CASE_RESULT:")) {
                String dataPart = line.substring("TEST_CASE_RESULT: ".length());

                try {
                    int firstCommaIndex = dataPart.indexOf(',');
                    int index = Integer.parseInt(dataPart.substring(0, firstCommaIndex).trim());

                    int lastCommaIndex = dataPart.lastIndexOf(',');
                    int secondLastCommaIndex = dataPart.lastIndexOf(',', lastCommaIndex - 1);

                    String actualOutput = dataPart.substring(firstCommaIndex + 1, secondLastCommaIndex).trim();

                    String durationString = dataPart.substring(secondLastCommaIndex + 1, lastCommaIndex).trim();
                    long duration = Long.parseLong(durationString);

                    String errorMessage = null;
                    String errorType = null;
                    if (lastCommaIndex < dataPart.length() - 1) {
                        String errorInfo = dataPart.substring(lastCommaIndex + 1).trim();
                        if (!errorInfo.isEmpty()) {
                            int colonIndex = errorInfo.indexOf(':');
                            if (colonIndex != -1) {
                                errorType = errorInfo.substring(0, colonIndex).trim();
                                errorMessage = errorInfo.substring(colonIndex + 1).trim();
                            } else {
                                errorType = errorInfo;
                                errorMessage = errorInfo;
                            }
                        }
                    }

                    ExecutionResult.TestCaseOutput testCaseOutput = ExecutionResult.TestCaseOutput.builder()
                            .testCaseIndex(index)
                            .actualOutput(actualOutput)
                            .executionTimeMs(duration)
                            .errorMessage(errorMessage)
                            .errorType(errorType)
                            .memoryBytes(memoryBytes)
                            .build();

                    results.add(testCaseOutput);
                    parsedCount++;

                    logConsumer.accept("PARSE_SUCCESS: Test case " + index + " - output: " + actualOutput
                            + ", duration: " + duration + "ms, memory: "
                            + (memoryBytes != null ? memoryBytes + " bytes" : "unavailable"));

                } catch (Exception e) {
                    logConsumer.accept("PARSE_ERROR: Failed to parse line: " + line + " - "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }

        logConsumer.accept("PARSE_COMPLETE: Parsed " + parsedCount + " test case results");
    }
}

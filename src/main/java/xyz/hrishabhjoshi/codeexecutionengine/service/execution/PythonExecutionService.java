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

@Service("pythonExecutionService")
@RequiredArgsConstructor
public class PythonExecutionService implements ExecutionService {

    private final ManagedProcessRunner processRunner;
    private final ExecutionRuntimeProperties runtimeProperties;

    @org.springframework.beans.factory.annotation.Value("${execution.worker.timeout-seconds:10}")
    private long executionTimeoutSeconds;

    @Override
    public String getLanguage() {
        return "python";
    }

    @Override
    public ExecutionResult run(String submissionId, Path submissionPath, String fullyQualifiedMainClass,
                               Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("EXECUTION_SERVICE: Starting worker-pod execution for submission: " + submissionId);

        String pythonFilePath = constructPythonFilePath(fullyQualifiedMainClass);
        logConsumer.accept("EXECUTION_SERVICE: Constructed Python file path: " + pythonFilePath);

        List<String> command = new ArrayList<>(runtimeProperties.getRequiredLanguageRuntime("python").runCommandTokens());
        command.add(pythonFilePath);

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
        parseExecutionOutputForResults(result.output(), testCaseOutputs, null);

        return ExecutionResult.builder()
                .rawOutput(result.output())
                .testCaseOutputs(testCaseOutputs)
                .timedOut(result.timedOut())
                .exitCode(result.exitCode())
                .build();
    }

    private String constructPythonFilePath(String fullyQualifiedMainClass) {
        if (fullyQualifiedMainClass == null || fullyQualifiedMainClass.trim().isEmpty()) {
            throw new IllegalArgumentException("fullyQualifiedMainClass cannot be null or empty for Python execution");
        }

        String packagePath = fullyQualifiedMainClass;
        if (packagePath.endsWith(".Main")) {
            packagePath = packagePath.substring(0, packagePath.length() - 5);
        }

        String filePath = packagePath.replace(".", "/");
        return filePath + "/main.py";
    }

    private void parseExecutionOutputForResults(String output, List<ExecutionResult.TestCaseOutput> results, Long memoryBytes) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("TEST_CASE_RESULT:")) {
                String dataPart = line.substring("TEST_CASE_RESULT: ".length()).trim();

                try {
                    int firstCommaIndex = dataPart.indexOf(',');
                    if (firstCommaIndex == -1) {
                        throw new IllegalArgumentException("No comma found in test case result line");
                    }

                    int index = Integer.parseInt(dataPart.substring(0, firstCommaIndex).trim());
                    String remainingPart = dataPart.substring(firstCommaIndex + 1);

                    int lastCommaIndex = remainingPart.lastIndexOf(',');
                    if (lastCommaIndex == -1) {
                        throw new IllegalArgumentException("No second comma found in test case result line");
                    }

                    String jsonOutput = remainingPart.substring(0, lastCommaIndex);
                    String durationAndError = remainingPart.substring(lastCommaIndex + 1);

                    String errorMessage = null;
                    String errorType = null;
                    long duration = 0;

                    if (durationAndError.trim().isEmpty()) {
                        duration = 0;
                    } else {
                        String[] durationParts = durationAndError.split(",", 2);
                        try {
                            duration = Long.parseLong(durationParts[0].trim());
                        } catch (NumberFormatException e) {
                            duration = 0;
                            errorMessage = durationAndError.trim();
                            errorType = "ExecutionError";
                        }

                        if (durationParts.length > 1 && !durationParts[1].trim().isEmpty()) {
                            String errorInfo = durationParts[1].trim();
                            if (errorInfo.contains(":")) {
                                int colonIndex = errorInfo.indexOf(':');
                                errorType = errorInfo.substring(0, colonIndex).trim();
                                errorMessage = errorInfo.substring(colonIndex + 1).trim();
                            } else {
                                errorMessage = errorInfo;
                                errorType = "RuntimeError";
                            }
                        }
                    }

                    String actualOutput = jsonOutput.trim();
                    if ("null".equals(actualOutput) || actualOutput.isEmpty()) {
                        actualOutput = null;
                    }

                    results.add(ExecutionResult.TestCaseOutput.builder()
                            .testCaseIndex(index)
                            .actualOutput(actualOutput)
                            .executionTimeMs(duration)
                            .errorMessage(errorMessage)
                            .errorType(errorType)
                            .memoryBytes(memoryBytes)
                            .build());

                } catch (Exception e) {
                    System.err.println("EXECUTION_SERVICE_ERROR: Failed to parse test case result line: " + line + " - " + e.getMessage());

                    try {
                        String[] parts = dataPart.split(",", 2);
                        if (parts.length >= 1) {
                            int index = Integer.parseInt(parts[0].trim());
                            results.add(ExecutionResult.TestCaseOutput.builder()
                                    .testCaseIndex(index)
                                    .actualOutput(null)
                                    .executionTimeMs(0L)
                                    .errorMessage("Failed to parse execution output: " + e.getMessage())
                                    .errorType("ParseError")
                                    .build());
                        }
                    } catch (Exception parseException) {
                        System.err.println("EXECUTION_SERVICE_ERROR: Could not extract test case index: " + parseException.getMessage());
                    }
                }
            }
        }
    }
}

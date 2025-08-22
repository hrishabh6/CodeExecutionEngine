package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service("pythonExecutionService")
public class PythonExecutionService implements ExecutionService {

    private static final String DOCKER_IMAGE = "hrishabhjoshi/python-runtime:3.9";
    private static final long EXECUTION_TIMEOUT_SECONDS = 10;

    @Override
    public String getLanguage() {
        return "python";
    }

    @Override
    public ExecutionResult run(String submissionId, Path submissionPath, String fullyQualifiedMainClass, Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("EXECUTION_SERVICE: Starting execution for submission: " + submissionId);

        StringBuilder fullExecutionLog = new StringBuilder();
        List<ExecutionResult.TestCaseOutput> testCaseOutputs = new ArrayList<>();
        boolean timedOut = false;
        int exitCode = -1;

        ProcessBuilder runPb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", submissionPath.toAbsolutePath().toString() + ":/app",
                "-w", "/app",
                DOCKER_IMAGE,
                "python3",
                "com/algocrack/solution/q9/main.py"
        );

        runPb.redirectErrorStream(true);

        Process runProcess = runPb.start();
        logConsumer.accept("EXECUTION_SERVICE: Docker execution process started.");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fullExecutionLog.append(line).append("\n");
                logConsumer.accept("EXECUTION_SERVICE_LOG: " + line);
            }
        }

        boolean finished = runProcess.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            runProcess.destroyForcibly();
            timedOut = true;
            logConsumer.accept("EXECUTION_SERVICE: Execution timed out after " + EXECUTION_TIMEOUT_SECONDS + " seconds.");
            parseExecutionOutputForResults(fullExecutionLog.toString(), testCaseOutputs);
            exitCode = -999;
        } else {
            exitCode = runProcess.exitValue();
            logConsumer.accept("EXECUTION_SERVICE: Execution completed with exit code: " + exitCode);
            parseExecutionOutputForResults(fullExecutionLog.toString(), testCaseOutputs);
        }

        return ExecutionResult.builder()
                .rawOutput(fullExecutionLog.toString())
                .testCaseOutputs(testCaseOutputs)
                .timedOut(timedOut)
                .exitCode(exitCode)
                .build();
    }

    private void parseExecutionOutputForResults(String output, List<ExecutionResult.TestCaseOutput> results) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("TEST_CASE_RESULT:")) {
                String dataPart = line.substring("TEST_CASE_RESULT: ".length()).trim();

                try {
                    // Split only on the first comma to get the index
                    int firstCommaIndex = dataPart.indexOf(',');
                    if (firstCommaIndex == -1) {
                        throw new IllegalArgumentException("No comma found in test case result line");
                    }

                    int index = Integer.parseInt(dataPart.substring(0, firstCommaIndex).trim());
                    String remainingPart = dataPart.substring(firstCommaIndex + 1);

                    // Find the last comma to separate duration from the rest
                    int lastCommaIndex = remainingPart.lastIndexOf(',');
                    if (lastCommaIndex == -1) {
                        throw new IllegalArgumentException("No second comma found in test case result line");
                    }

                    String jsonOutput = remainingPart.substring(0, lastCommaIndex);
                    String durationAndError = remainingPart.substring(lastCommaIndex + 1);

                    String errorMessage = null;
                    String errorType = null;
                    long duration = 0;

                    // Parse duration and potential error
                    if (durationAndError.trim().isEmpty()) {
                        // Just a trailing comma, no error
                        duration = 0;
                    } else {
                        // Check if there's error information after duration
                        String[] durationParts = durationAndError.split(",", 2);
                        try {
                            duration = Long.parseLong(durationParts[0].trim());
                        } catch (NumberFormatException e) {
                            // If first part isn't a number, might be an error message
                            duration = 0;
                            errorMessage = durationAndError.trim();
                            errorType = "ExecutionError";
                        }

                        // Handle error information if present
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

                    // Handle special cases for JSON output
                    String actualOutput = jsonOutput.trim();
                    if ("null".equals(actualOutput)) {
                        actualOutput = null;
                    } else if (actualOutput.isEmpty()) {
                        actualOutput = null;
                    }

                    results.add(ExecutionResult.TestCaseOutput.builder()
                            .testCaseIndex(index)
                            .actualOutput(actualOutput)
                            .executionTimeMs(duration)
                            .errorMessage(errorMessage)
                            .errorType(errorType)
                            .build());

                    System.out.println("DEBUG: Successfully parsed test case " + index +
                            " with output: " + actualOutput +
                            " and duration: " + duration + "ms");

                } catch (Exception e) {
                    System.err.println("EXECUTION_SERVICE_ERROR: Failed to parse test case result line: " + line + " - " + e.getMessage());
                    e.printStackTrace();

                    // Try to extract at least the test case index for error reporting
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
                        System.err.println("EXECUTION_SERVICE_ERROR: Could not even extract test case index: " + parseException.getMessage());
                    }
                }
            }
        }
    }
}
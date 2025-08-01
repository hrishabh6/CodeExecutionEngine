package com.hrishabh.codeexecutionengine.service.execution;


import com.hrishabh.codeexecutionengine.dto.ExecutionResult; // Corrected import to new ExecutionResult DTO
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class JavaExecutionService implements ExecutionService {

    private static final String DOCKER_IMAGE = "openjdk:17-jdk-slim";
    private static final long EXECUTION_TIMEOUT_SECONDS = 10;

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public ExecutionResult run(String submissionId, Path submissionPath, String fullyQualifiedMainClass, Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("EXECUTION_SERVICE: Starting execution for submission: " + submissionId);

        StringBuilder fullExecutionLog = new StringBuilder();
        List<ExecutionResult.TestCaseOutput> testCaseOutputs = new ArrayList<>(); // Changed to TestCaseOutput
        boolean timedOut = false;
        int exitCode = -1;

        ProcessBuilder runPb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-m", "256m", // Memory limit: 256MB
                "--cpus", "0.5", // CPU limit: 0.5 of a CPU core
                "-v", submissionPath.toAbsolutePath().toString() + ":/app",
                "-w", "/app", // The classpath implicitly includes /app
                DOCKER_IMAGE,
                "java", fullyQualifiedMainClass // Execute with fully qualified class name
        );
        runPb.redirectErrorStream(true); // Merge stdout and stderr

        Process runProcess = runPb.start();
        logConsumer.accept("EXECUTION_SERVICE: Docker execution process started.");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fullExecutionLog.append(line).append("\n");
                logConsumer.accept("EXECUTION_SERVICE_LOG: " + line); // Real-time logging
            }
        }

        boolean finished = runProcess.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            runProcess.destroyForcibly();
            timedOut = true;
            logConsumer.accept("EXECUTION_SERVICE: Execution timed out after " + EXECUTION_TIMEOUT_SECONDS + " seconds.");
            parseExecutionOutputForResults(fullExecutionLog.toString(), testCaseOutputs); // Pass new list type
            exitCode = -999; // Custom exit code for timeout
        } else {
            exitCode = runProcess.exitValue();
            logConsumer.accept("EXECUTION_SERVICE: Execution completed with exit code: " + exitCode);
            parseExecutionOutputForResults(fullExecutionLog.toString(), testCaseOutputs); // Pass new list type
        }

        return new ExecutionResult(fullExecutionLog.toString(), testCaseOutputs, timedOut, exitCode);
    }

    /**
     * Parses the raw execution output to extract structured test case outputs.
     * This relies on the specific "TEST_CASE_RESULT:" prefix from your generated Main.java.
     * Expected format: "TEST_CASE_RESULT: <index>,<actualOutput>,<duration>,<optional_error_type>:<optional_error_message>"
     * Example SUCCESS: "TEST_CASE_RESULT: 0,3,15,"
     * Example ERROR:   "TEST_CASE_RESULT: 1,,20,ArithmeticException: / by zero"
     */
    private void parseExecutionOutputForResults(String output, List<ExecutionResult.TestCaseOutput> results) { // Changed list type
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("TEST_CASE_RESULT:")) {
                String dataPart = line.substring("TEST_CASE_RESULT: ".length());
                String[] parts = dataPart.split(",", 4); // Split into max 4 parts: index, actual, duration, error_info (the rest)

                if (parts.length >= 3) { // Minimum required: index, actualOutput, duration
                    try {
                        int index = Integer.parseInt(parts[0].trim());
                        String actual = parts[1].trim();
                        long duration = Long.parseLong(parts[2].trim());

                        String errorMessage = null;
                        String errorType = null;

                        if (parts.length == 4) { // Error information is present
                            String errorInfo = parts[3].trim();
                            if (!errorInfo.isEmpty()) {
                                int colonIndex = errorInfo.indexOf(':');
                                if (colonIndex != -1) {
                                    errorType = errorInfo.substring(0, colonIndex).trim();
                                    errorMessage = errorInfo.substring(colonIndex + 1).trim();
                                } else {
                                    // If no colon, assume the whole thing is the error message/type
                                    errorType = errorInfo;
                                    errorMessage = errorInfo; // Fallback: put full info in message
                                }
                            }
                        }

                        // Build the TestCaseOutput object using the builder for clarity
                        results.add(ExecutionResult.TestCaseOutput.builder()
                                .testCaseIndex(index)
                                .actualOutput(actual)
                                .executionTimeMs(duration)
                                .errorMessage(errorMessage)
                                .errorType(errorType)
                                .build());

                    } catch (NumberFormatException e) {
                        System.err.println("EXECUTION_SERVICE_ERROR: Failed to parse number in test case result line: " + line + " - " + e.getMessage());
                    } catch (ArrayIndexOutOfBoundsException e) {
                        System.err.println("EXECUTION_SERVICE_ERROR: Malformed test case result line (too few parts): " + line + " - " + e.getMessage());
                    }
                } else {
                    System.err.println("EXECUTION_SERVICE_ERROR: Malformed test case result line (invalid format): " + line);
                }
            }
        }
    }
}
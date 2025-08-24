package xyz.hrishabhjoshi.codeexecutionengine.service.execution;


import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult; // Corrected import to new ExecutionResult DTO
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

    private static final String DOCKER_IMAGE = "hrishabhjoshi/my-java-runtime:17";
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
        List<ExecutionResult.TestCaseOutput> testCaseOutputs = new ArrayList<>();
        boolean timedOut = false;
        int exitCode = -1;

        ProcessBuilder runPb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-m", "256m",
                "--cpus", "0.5",
                "-v", submissionPath.toAbsolutePath().toString() + ":/app/src", // 💡 Mount to /app/src
                "-w", "/app",
                DOCKER_IMAGE,
                "java",
                "-cp", "/app/src:/app/libs/*", // 💡 Classpath includes /app/src for the compiled classes
                fullyQualifiedMainClass
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

    /**
     * Parses the raw execution output to extract structured test case outputs.
     * This relies on the specific "TEST_CASE_RESULT:" prefix from your generated Main.java.
     * Expected format: "TEST_CASE_RESULT: <index>,<actualOutput>,<duration>,<optional_error_type>:<optional_error_message>"
     * Example SUCCESS: "TEST_CASE_RESULT: 0,3,15,"
     * Example ERROR:   "TEST_CASE_RESULT: 1,,20,ArithmeticException: / by zero"
     */


    private void parseExecutionOutputForResults(String output, List<ExecutionResult.TestCaseOutput> results) {
        String[] lines = output.split("\n");
        for (String line : lines) {
            if (line.startsWith("TEST_CASE_RESULT:")) {
                String dataPart = line.substring("TEST_CASE_RESULT: ".length());

                try {
                    // Find the first comma to get the index
                    int firstCommaIndex = dataPart.indexOf(',');
                    int index = Integer.parseInt(dataPart.substring(0, firstCommaIndex).trim());

                    // Find the last comma to get the duration and error info
                    int lastCommaIndex = dataPart.lastIndexOf(',');
                    int secondLastCommaIndex = dataPart.lastIndexOf(',', lastCommaIndex - 1);

                    // Extract the actual output part, which is between the first and second-to-last comma
                    String actualOutput = dataPart.substring(firstCommaIndex + 1, secondLastCommaIndex).trim();

                    // Extract the duration part, which is between the second and last comma
                    String durationString = dataPart.substring(secondLastCommaIndex + 1, lastCommaIndex).trim();
                    long duration = Long.parseLong(durationString);

                    String errorMessage = null;
                    String errorType = null;
                    if (lastCommaIndex < dataPart.length() - 1) {
                        // Extract the error info if it exists after the last comma
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

                    results.add(ExecutionResult.TestCaseOutput.builder()
                            .testCaseIndex(index)
                            .actualOutput(actualOutput)
                            .executionTimeMs(duration)
                            .errorMessage(errorMessage)
                            .errorType(errorType)
                            .build());
                } catch (Exception e) {
                    System.err.println("EXECUTION_SERVICE_ERROR: Failed to parse test case result line: " + line + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

    }}
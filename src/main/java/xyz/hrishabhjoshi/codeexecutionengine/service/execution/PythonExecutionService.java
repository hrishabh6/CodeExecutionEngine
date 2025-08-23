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

/**
 * Python code execution service that runs Python code in a Docker container.
 *
 * <p>This service handles the execution of Python submissions by:
 * <ul>
 *   <li>Converting Java-style package names to Python file paths</li>
 *   <li>Running the code in a secure Docker environment</li>
 *   <li>Parsing execution results from the Python output</li>
 *   <li>Handling timeouts and error conditions</li>
 * </ul>
 * </p>
 *
 * @author Hrishabhj Joshi
 * @version 1.0
 * @since 1.0
 */
@Service("pythonExecutionService")
public class PythonExecutionService implements ExecutionService {

    private static final String DOCKER_IMAGE = "hrishabhjoshi/python-runtime:3.9";
    private static final long EXECUTION_TIMEOUT_SECONDS = 10;

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLanguage() {
        return "python";
    }

    /**
     * Executes Python code in a Docker container and returns the execution results.
     *
     * <p>The method performs the following operations:
     * <ul>
     *   <li>Constructs the Python file path from the fully qualified class name</li>
     *   <li>Creates and runs a Docker container with the submission code</li>
     *   <li>Monitors execution with timeout handling</li>
     *   <li>Parses the output to extract test case results</li>
     * </ul>
     * </p>
     *
     * @param submissionId The unique identifier for this submission
     * @param submissionPath The path to the submission files on the host system
     * @param fullyQualifiedMainClass The Java-style package name to be converted to Python path
     * @param logConsumer Consumer function for handling log messages
     * @return ExecutionResult containing test case outputs and execution metadata
     * @throws IOException if file I/O operations fail
     * @throws InterruptedException if the execution process is interrupted
     */
    @Override
    public ExecutionResult run(String submissionId, Path submissionPath, String fullyQualifiedMainClass, Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("EXECUTION_SERVICE: Starting execution for submission: " + submissionId);

        String pythonFilePath = constructPythonFilePath(fullyQualifiedMainClass);
        logConsumer.accept("EXECUTION_SERVICE: Constructed Python file path: " + pythonFilePath);

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
                pythonFilePath
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
     * Converts a Java-style fully qualified class name to a Python file path.
     *
     * <p>This method transforms package notation used in Java to the corresponding
     * file system path structure expected by Python modules.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"com.algocrack.solution.q9.Main" → "com/algocrack/solution/q9/main.py"</li>
     *   <li>"org.example.test.q1.Main" → "org/example/test/q1/main.py"</li>
     * </ul>
     *
     * @param fullyQualifiedMainClass The Java-style fully qualified class name
     * @return The corresponding Python file path
     * @throws IllegalArgumentException if the input is null or empty
     */
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

    /**
     * Parses the raw execution output to extract structured test case results.
     *
     * <p>This method looks for specially formatted output lines that contain
     * test case results in the format:</p>
     * <pre>TEST_CASE_RESULT: &lt;index&gt;,&lt;output&gt;,&lt;duration&gt;,&lt;error_info&gt;</pre>
     *
     * <p>The parsing handles:</p>
     * <ul>
     *   <li>Successful test case executions with JSON output</li>
     *   <li>Error conditions with exception details</li>
     *   <li>Execution timing information</li>
     *   <li>Malformed output lines (logged as parse errors)</li>
     * </ul>
     *
     * @param output The raw execution output from the Python process
     * @param results The list to populate with parsed test case results
     */
    private void parseExecutionOutputForResults(String output, List<ExecutionResult.TestCaseOutput> results) {
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
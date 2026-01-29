package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import org.springframework.stereotype.Service;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.MemoryParser;
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
 *  
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
        Long memoryBytes = null;

        // Generate unique container name for memory tracking
        String containerName = "exec-" + submissionId + "-" + System.currentTimeMillis();
        logConsumer.accept("MEMORY_CONTAINER: " + containerName);

        ProcessBuilder runPb = new ProcessBuilder(
                "docker", "run",
                "--name", containerName,  // Named container for stats tracking
                "-v", submissionPath.toAbsolutePath().toString() + ":/app",
                "-w", "/app",
                DOCKER_IMAGE,
                "python3",
                pythonFilePath
        );

        runPb.redirectErrorStream(true);

        Process runProcess = runPb.start();
        logConsumer.accept("EXECUTION_SERVICE: Docker execution process started.");

        // Start a thread to capture memory stats while container is running
        final Long[] capturedMemory = {null};
        final int[] attemptCount = {0};
        Thread statsThread = new Thread(() -> {
            try {
                logConsumer.accept("MEMORY_THREAD: Starting stats collection for container: " + containerName);
                // Wait a bit for container to actually start
                Thread.sleep(500);
                // Capture memory every 500ms while container runs
                for (int i = 0; i < 20; i++) { // Max 10 seconds
                    attemptCount[0]++;
                    Long mem = getContainerMemoryUsage(containerName, logConsumer);
                    if (mem != null && (capturedMemory[0] == null || mem > capturedMemory[0])) {
                        capturedMemory[0] = mem; // Keep peak memory
                        logConsumer.accept("MEMORY_THREAD: Captured peak memory: " + mem + " bytes (attempt " + attemptCount[0] + ")");
                    }
                    Thread.sleep(500);
                }
                logConsumer.accept("MEMORY_THREAD: Completed " + attemptCount[0] + " attempts, peak memory: " + capturedMemory[0]);
            } catch (InterruptedException e) {
                logConsumer.accept("MEMORY_THREAD: Interrupted after " + attemptCount[0] + " attempts");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logConsumer.accept("MEMORY_THREAD: Error: " + e.getMessage());
            }
        });
        statsThread.setDaemon(true);
        statsThread.start();

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
            parseExecutionOutputForResults(fullExecutionLog.toString(), testCaseOutputs, capturedMemory[0]);
            exitCode = -999;
        } else {
            exitCode = runProcess.exitValue();
            logConsumer.accept("EXECUTION_SERVICE: Execution completed with exit code: " + exitCode);

            // Use captured memory from stats thread
            memoryBytes = capturedMemory[0];

            if (memoryBytes != null) {
                logConsumer.accept("MEMORY_CAPTURED: Peak memory usage: " + memoryBytes + " bytes (" + MemoryParser.bytesToKB(memoryBytes) + " KB)");
            } else {
                // Fallback if stats not captured (use reasonable default for Python)
                memoryBytes = 128L * 1024L * 1024L; // 128 MB in bytes
                logConsumer.accept("MEMORY_FALLBACK: No memory captured, using default estimate as fallback: " + memoryBytes + " bytes");
            }

            parseExecutionOutputForResults(fullExecutionLog.toString(), testCaseOutputs, memoryBytes);
        }

        // Stop stats thread
        statsThread.interrupt();

        // Clean up container
        // Clean up container
        try {
            cleanupContainer(containerName, logConsumer);
        } catch (Exception e) {
            logConsumer.accept("CLEANUP_ERROR: " + e.getMessage());
        }

        return ExecutionResult.builder()
                .rawOutput(fullExecutionLog.toString())
                .testCaseOutputs(testCaseOutputs)
                .timedOut(timedOut)
                .exitCode(exitCode)
                .build();
    }

    /**
     * Get memory usage from Docker stats.
     * 
     * @param containerName Name of the container
     * @param logConsumer Logger
     * @return Peak memory in bytes, or null if failed
     */
    private Long getContainerMemoryUsage(String containerName, Consumer<String> logConsumer) {
        try {
            ProcessBuilder statsPb = new ProcessBuilder(
                "docker", "stats", "--no-stream", 
                "--format", "{{.MemUsage}}", 
                containerName
            );
            
            Process statsProcess = statsPb.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(statsProcess.getInputStream()))) {
                String memoryStats = reader.readLine();
                
                if (memoryStats != null && !memoryStats.trim().isEmpty()) {
                    logConsumer.accept("MEMORY_STATS: " + memoryStats);
                    Long bytes = MemoryParser.parseMemoryToBytes(memoryStats);
                    if (bytes != null) {
                        logConsumer.accept("MEMORY_PARSED: " + bytes + " bytes (" + MemoryParser.bytesToKB(bytes) + " KB)");
                    }
                    return bytes;
                }
            }
            
            statsProcess.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            logConsumer.accept("MEMORY_TRACKING_ERROR: " + e.getMessage());
        }
        return null;
    }

    /**
     * Clean up Docker container.
     * 
     * @param containerName Name of the container to remove
     * @param logConsumer Logger
     */
    private void cleanupContainer(String containerName, Consumer<String> logConsumer) {
        try {
            ProcessBuilder rmPb = new ProcessBuilder("docker", "rm", "-f", containerName);
            Process rmProcess = rmPb.start();
            boolean removed = rmProcess.waitFor(5, TimeUnit.SECONDS);
            if (removed) {
                logConsumer.accept("CLEANUP: Removed container " + containerName);
            } else {
                logConsumer.accept("CLEANUP_WARNING: Container removal timed out for " + containerName);
            }
        } catch (Exception e) {
            logConsumer.accept("CLEANUP_ERROR: " + e.getMessage());
        }
    }

    /**
     * Converts a Java-style fully qualified class name to a Python file path.
     *
     * <p>This method transforms package notation used in Java to the corresponding
     * file system path structure expected by Python modules. 
     *
     * <p>Examples: 
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
     * test case results in the format: 
     * <pre>TEST_CASE_RESULT: &lt;index&gt;,&lt;output&gt;,&lt;duration&gt;,&lt;error_info&gt;</pre>
     *
     * <p>The parsing handles: 
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
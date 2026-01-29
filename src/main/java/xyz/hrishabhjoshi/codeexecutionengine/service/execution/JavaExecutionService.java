package xyz.hrishabhjoshi.codeexecutionengine.service.execution;

import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.MemoryParser;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
public class JavaExecutionService implements ExecutionService {

    private static final String DOCKER_IMAGE = "hrishabhjoshi/my-java-runtime:17";
    private static final long EXECUTION_TIMEOUT_SECONDS = 10;

    // Optimized memory sampling configuration
    private static final int INITIAL_WAIT_MS = 50;  // Reduced from 100ms for faster startup
    private static final int SAMPLE_INTERVAL_MS = 150;  // Balanced between accuracy and overhead
    private static final int MAX_SAMPLES = 60;  // 60 * 150ms = 9 seconds of sampling

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public ExecutionResult run(String submissionId, Path submissionPath, String fullyQualifiedMainClass,
                               Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("EXECUTION_SERVICE: Starting execution for submission: " + submissionId);

        StringBuilder fullExecutionLog = new StringBuilder();
        List<ExecutionResult.TestCaseOutput> testCaseOutputs = new ArrayList<>();
        boolean timedOut = false;
        int exitCode = -1;

        // Generate unique container name for memory tracking
        String containerName = "exec-" + submissionId + "-" + System.currentTimeMillis();
        logConsumer.accept("MEMORY_CONTAINER: " + containerName);

        ProcessBuilder runPb = new ProcessBuilder(
                "docker", "run",
                "--name", containerName,
                "--rm=false",  // Don't auto-remove - we need to get stats after completion
                "-m", "256m",
                "--memory-swap", "256m",  // Prevent swap usage for consistent memory measurement
                "--cpus", "0.5",
                "--cpu-shares", "512",  // Fair CPU scheduling
                "--pids-limit", "100",  // Limit process count to prevent fork bombs
                "--network", "none",  // Disable network for security and performance
                "-v", submissionPath.toAbsolutePath().toString() + ":/app/src:ro",  // Read-only mount
                "-w", "/app",
                DOCKER_IMAGE,
                "java",
                "-XX:+UseContainerSupport",  // Enable container-aware JVM settings
                "-XX:MaxRAMPercentage=75.0",  // Use up to 75% of container memory (192MB of 256MB)
                "-XX:+TieredCompilation",  // Enable tiered compilation for faster startup
                "-XX:TieredStopAtLevel=1",  // Stop at C1 compiler (faster compilation, good for short runs)
                "-cp", "/app/src:/app/libs/*",
                fullyQualifiedMainClass);
        runPb.redirectErrorStream(true);

        Process runProcess = runPb.start();
        logConsumer.accept("EXECUTION_SERVICE: Docker execution process started.");

        // Use AtomicLong for thread-safe peak memory tracking
        final AtomicLong peakMemoryBytes = new AtomicLong(0);
        final AtomicInteger attemptCount = new AtomicInteger(0);
        final AtomicInteger successfulReads = new AtomicInteger(0);
        final AtomicLong firstSuccessfulReadTime = new AtomicLong(0);

        // Start a thread to capture memory stats while container is running
        Thread statsThread = new Thread(() -> {
            try {
                logConsumer.accept("MEMORY_THREAD: Starting stats collection for container: " + containerName);

                // Reduced initial wait for faster first sample
                Thread.sleep(INITIAL_WAIT_MS);

                // Capture memory with optimized sampling
                for (int i = 0; i < MAX_SAMPLES; i++) {
                    int attempt = attemptCount.incrementAndGet();

                    Long mem = getContainerMemoryUsage(containerName, logConsumer);

                    if (mem != null && mem > 0) {
                        int successCount = successfulReads.incrementAndGet();

                        // Record time of first successful read
                        if (successCount == 1) {
                            firstSuccessfulReadTime.set(System.currentTimeMillis());
                        }

                        long currentPeak = peakMemoryBytes.get();

                        // Update peak memory atomically
                        while (mem > currentPeak) {
                            if (peakMemoryBytes.compareAndSet(currentPeak, mem)) {
                                logConsumer.accept("MEMORY_THREAD: New peak memory: " + mem + " bytes ("
                                        + MemoryParser.bytesToKB(mem) + " KB) at attempt " + attempt);
                                break;
                            }
                            currentPeak = peakMemoryBytes.get();
                        }
                    }

                    Thread.sleep(SAMPLE_INTERVAL_MS);
                }

                logConsumer.accept("MEMORY_THREAD: Completed " + attemptCount.get()
                        + " attempts, " + successfulReads.get() + " successful reads, peak memory: "
                        + peakMemoryBytes.get() + " bytes");

            } catch (InterruptedException e) {
                logConsumer.accept("MEMORY_THREAD: Interrupted after " + attemptCount.get()
                        + " attempts, " + successfulReads.get() + " successful reads");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                logConsumer.accept("MEMORY_THREAD: Error: " + e.getClass().getName() + " - " + e.getMessage());
            }
        });
        statsThread.setDaemon(true);
        statsThread.start();

        // Read execution output in real-time to avoid blocking
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(runProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fullExecutionLog.append(line).append("\n");
                logConsumer.accept("EXECUTION_SERVICE_LOG: " + line);
            }
        }

        boolean finished = runProcess.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Stop stats thread and wait for it to complete
        statsThread.interrupt();
        try {
            statsThread.join(500);  // Reduced from 1000ms for faster completion
        } catch (InterruptedException e) {
            logConsumer.accept("MEMORY_THREAD_JOIN: Interrupted while waiting for stats thread");
            Thread.currentThread().interrupt();
        }

        // Get final peak memory value
        Long finalMemoryBytes = peakMemoryBytes.get() > 0 ? peakMemoryBytes.get() : null;

        if (!finished) {
            runProcess.destroyForcibly();
            timedOut = true;
            logConsumer.accept("EXECUTION_SERVICE: Execution timed out after " + EXECUTION_TIMEOUT_SECONDS + " seconds.");
            exitCode = -999;
        } else {
            exitCode = runProcess.exitValue();
            logConsumer.accept("EXECUTION_SERVICE: Execution completed with exit code: " + exitCode);
        }

        // Log final memory status
        if (finalMemoryBytes != null) {
            logConsumer.accept("MEMORY_FINAL: Peak memory usage: " + finalMemoryBytes + " bytes ("
                    + MemoryParser.bytesToKB(finalMemoryBytes) + " KB)");
        } else {
            logConsumer.accept("MEMORY_FINAL: No memory data captured - container may have exited too quickly");
            // Try one final stats check before cleanup
            finalMemoryBytes = getContainerMemoryUsage(containerName, logConsumer);
            if (finalMemoryBytes != null) {
                logConsumer.accept("MEMORY_FINAL_RETRY: Captured " + finalMemoryBytes + " bytes on final attempt");
            }
        }

        // Parse execution output and set memory for each test case
        parseExecutionOutputForResults(fullExecutionLog.toString(), testCaseOutputs, finalMemoryBytes, logConsumer);

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
     * Get memory usage from Docker stats with minimal overhead.
     *
     * @param containerName Name of the container
     * @param logConsumer   Logger
     * @return Peak memory in bytes, or null if failed
     */
    private Long getContainerMemoryUsage(String containerName, Consumer<String> logConsumer) {
        try {
            ProcessBuilder statsPb = new ProcessBuilder(
                    "docker", "stats", "--no-stream",
                    "--format", "{{.MemUsage}}",
                    containerName);
            statsPb.redirectErrorStream(true);

            Process statsProcess = statsPb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(statsProcess.getInputStream()), 256)) {  // Smaller buffer for faster reads
                String memoryStats = reader.readLine();

                if (memoryStats != null && !memoryStats.trim().isEmpty()) {
                    // Check if it's an error message
                    if (memoryStats.contains("No such container") ||
                            memoryStats.contains("Error") ||
                            memoryStats.contains("Cannot connect")) {
                        // Don't log every error to reduce overhead
                        return null;
                    }

                    Long bytes = MemoryParser.parseMemoryToBytes(memoryStats);

                    if (bytes != null && bytes > 0) {
                        return bytes;
                    }
                }
            }

            statsProcess.waitFor(1, TimeUnit.SECONDS);  // Reduced timeout for faster failure detection
        } catch (Exception e) {
            // Silent failure for memory tracking to avoid log spam
        }
        return null;
    }

    /**
     * Clean up Docker container efficiently.
     *
     * @param containerName Name of the container to remove
     * @param logConsumer   Logger
     */
    private void cleanupContainer(String containerName, Consumer<String> logConsumer) {
        try {
            ProcessBuilder rmPb = new ProcessBuilder("docker", "rm", "-f", containerName);
            rmPb.redirectErrorStream(true);
            Process rmProcess = rmPb.start();

            boolean removed = rmProcess.waitFor(3, TimeUnit.SECONDS);  // Reduced from 5s
            if (removed) {
                int exitCode = rmProcess.exitValue();
                if (exitCode == 0) {
                    logConsumer.accept("CLEANUP_SUCCESS: Removed container " + containerName);
                } else {
                    logConsumer.accept("CLEANUP_WARNING: Container removal exited with code " + exitCode);
                }
            } else {
                logConsumer.accept("CLEANUP_WARNING: Container removal timed out for " + containerName);
                rmProcess.destroyForcibly();
            }
        } catch (Exception e) {
            logConsumer.accept("CLEANUP_ERROR: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Parses the raw execution output to extract structured test case outputs.
     * Expected format: "TEST_CASE_RESULT: <index>,<actualOutput>,<duration>,<optional_error_type>:<optional_error_message>"
     */
    private void parseExecutionOutputForResults(String output, List<ExecutionResult.TestCaseOutput> results,
                                                Long memoryBytes, Consumer<String> logConsumer) {
        String[] lines = output.split("\n");
        int parsedCount = 0;

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

                    // Extract the actual output part
                    String actualOutput = dataPart.substring(firstCommaIndex + 1, secondLastCommaIndex).trim();

                    // Extract the duration part
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

                    // Create test case output with memory
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
                            + (memoryBytes != null ? memoryBytes + " bytes" : "null"));

                } catch (Exception e) {
                    logConsumer.accept("PARSE_ERROR: Failed to parse line: " + line + " - "
                            + e.getClass().getSimpleName() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        logConsumer.accept("PARSE_COMPLETE: Parsed " + parsedCount + " test case results, memory set to: "
                + (memoryBytes != null ? memoryBytes + " bytes (" + MemoryParser.bytesToKB(memoryBytes) + " KB)" : "null"));
    }
}
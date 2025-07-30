package com.hrishabh.codeexecutionengine.service.codeexecutionservice;

import com.hrishabh.codeexecutionengine.dto.CodeExecutionResultDTO;
import com.hrishabh.codeexecutionengine.dto.CompilationResult; // DTO for compilation service output
import com.hrishabh.codeexecutionengine.dto.ExecutionResult;   // DTO for execution service output
import com.hrishabh.codeexecutionengine.service.compilation.CompilationService; // Interface for compilation
import com.hrishabh.codeexecutionengine.service.compilation.ExecutionService;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList; // Needed for List.of() if using older Java or want mutable list
import java.util.List;
import java.util.function.Consumer;

@Service
public class CodeExecutorService {

    private final CompilationService compilationService;
    private final ExecutionService executionService;

    // Spring will auto-wire the concrete implementations (e.g., JavaCompilationService, JavaExecutionService)
    public CodeExecutorService(CompilationService compilationService, ExecutionService executionService) {
        this.compilationService = compilationService;
        this.executionService = executionService;
    }

    /**
     * Orchestrates the compilation and execution of Java code within a Docker container.
     * This service focuses on running the code and returning its raw outputs and status.
     * Comparison logic (e.g., PASSED/FAILED) is left to the consuming application.
     *
     * @param submissionId            The unique ID for the submission.
     * @param submissionRootPath      The absolute path to the directory containing Main.java and Solution.java.
     * This directory is also where compiled .class files will be located.
     * @param fullyQualifiedMainClass The fully qualified name of the Main class (e.g., "com.mycompany.Main").
     * @param logConsumer             A consumer to handle real-time log lines from Docker (e.g., System.out::println, or a Kafka producer).
     * @return A CodeExecutionResultDTO containing compilation status, raw execution logs,
     * and structured raw outputs/errors for each test case.
     */
    public CodeExecutionResultDTO executeCode(
            String submissionId,
            Path submissionRootPath,
            String fullyQualifiedMainClass,
            Consumer<String> logConsumer) { // Removed CodeSubmissionDTO from signature

        // Validate submission path
        File submissionDir = submissionRootPath.toFile();
        if (!submissionDir.exists() || !submissionDir.isDirectory()) {
            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .overallStatus("INTERNAL_ERROR")
                    .compilationOutput("Submission directory not found or not a directory: " + submissionRootPath)
                    .testCaseOutputs(List.of()) // Use List.of() for empty immutable list
                    .build();
        }

        String overallCompilationOutput = ""; // To store compilation messages for final DTO
        List<CodeExecutionResultDTO.TestCaseOutput> finalTestCaseOutputs = new ArrayList<>(); // To store processed test case outputs

        try {
            // --- Step 1: Compile Code ---
            CompilationResult compileResult = compilationService.compile(submissionId, submissionRootPath, logConsumer);
            overallCompilationOutput = compileResult.getOutput(); // Capture raw compilation output

            if (!compileResult.isSuccess()) {
                // If compilation fails, return with compilation error status and output
                return CodeExecutionResultDTO.builder()
                        .submissionId(submissionId)
                        .overallStatus("COMPILATION_ERROR")
                        .compilationOutput(overallCompilationOutput)
                        .testCaseOutputs(List.of()) // No test case outputs on compilation error
                        .build();
            }

            // --- Step 2: Execute Code ---
            // If compilation was successful, proceed to execution
            ExecutionResult runResult = executionService.run(submissionId, submissionRootPath, fullyQualifiedMainClass, logConsumer);

            String overallStatus;

            // Determine overall status based on execution outcome
            if (runResult.isTimedOut()) {
                overallStatus = "TIMEOUT";
            } else if (runResult.getExitCode() != 0) {
                // A non-zero exit code typically indicates a JVM-level error (e.g., unhandled exception, OOM)
                overallStatus = "RUNTIME_ERROR";
            } else {
                // If exit code is 0, execution completed without crashing the JVM.
                // The actual outputs of individual test cases (and any errors caught by Main.java's try-catch)
                // are contained within runResult.getTestCaseOutputs().
                // The consumer of this library will determine PASSED/FAILED.
                overallStatus = "SUCCESS"; // Code ran to completion without crashing
            }

            // Map ExecutionResult.TestCaseOutput to CodeExecutionResultDTO.TestCaseOutput
            // This is a direct mapping as the structures are now identical (or very similar)
            for (ExecutionResult.TestCaseOutput tcOutput : runResult.getTestCaseOutputs()) {
                finalTestCaseOutputs.add(CodeExecutionResultDTO.TestCaseOutput.builder()
                        .testCaseIndex(tcOutput.getTestCaseIndex())
                        .actualOutput(tcOutput.getActualOutput())
                        .executionTimeMs(tcOutput.getExecutionTimeMs())
                        .errorMessage(tcOutput.getErrorMessage())
                        .errorType(tcOutput.getErrorType())
                        .build());
            }

            // Combine compilation output and raw execution output for the final DTO's comprehensive log
            String combinedOutput = overallCompilationOutput + "\n" + runResult.getRawOutput();

            // Return the final aggregated result
            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .overallStatus(overallStatus)
                    .compilationOutput(combinedOutput) // Contains all logs (compile + runtime)
                    .testCaseOutputs(finalTestCaseOutputs) // Raw outputs/errors per test case
                    .build();

        } catch (IOException | InterruptedException e) {
            // Catching general issues like Docker daemon not running, process interruption etc.
            logConsumer.accept("ORCHESTRATION_SERVICE_ERROR: Internal error during code execution: " + e.getMessage());
            e.printStackTrace(); // Log the stack trace for debugging
            return CodeExecutionResultDTO.builder()
                    .submissionId(submissionId)
                    .overallStatus("INTERNAL_ERROR")
                    .compilationOutput(overallCompilationOutput + "\n" + "Orchestration/Docker communication failed: " + e.getMessage())
                    .testCaseOutputs(List.of())
                    .build();
        }
    }

    // The getExpectedOutputForIndex helper method is no longer needed here as comparison logic is removed
    // private String getExpectedOutputForIndex(...) { ... }
}
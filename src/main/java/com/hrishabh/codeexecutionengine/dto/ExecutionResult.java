package com.hrishabh.codeexecutionengine.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {
    private String rawOutput; // Full stdout/stderr from execution
    private List<ExecutionResult.TestCaseOutput> testCaseOutputs; // Renamed to clearly indicate it's raw output
    private boolean timedOut; // True if execution timed out
    private int exitCode;     // Exit code of the Java process in Docker

    /**
     * Represents the raw output collected for a single test case.
     * The comparison logic is left to the consumer of this library.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseOutput {
        private int testCaseIndex;     // The index of the test case (e.g., 0, 1, 2...)
        private String actualOutput;   // The raw string output produced by the user's code for this test case
        private long executionTimeMs;  // Time taken to execute this test case in milliseconds
        private String errorMessage;   // Any error message (e.g., stack trace snippet), null if none
        private String errorType;      // e.g., "ArithmeticException", "ArrayIndexOutOfBoundsException", null if no error
    }
}
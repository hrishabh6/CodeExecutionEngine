package com.hrishabh.codeexecutionengine.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeExecutionResultDTO {
    private String submissionId;
    private String overallStatus; // e.g., "COMPILATION_ERROR", "RUNTIME_ERROR", "SUCCESS", "TIMEOUT", "INTERNAL_ERROR"
    private String compilationOutput; // Stdout/stderr from compilation phase + raw execution log
    private List<TestCaseOutput> testCaseOutputs; // Renamed to accurately reflect what it holds

    /**
     * Represents the raw output collected for a single test case from the executed code.
     * Comparison with expected output is left to the consumer of this library.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseOutput { // Now directly using the same structure as ExecutionResult.TestCaseOutput
        private int testCaseIndex;     // The index of the test case
        private String actualOutput;   // The raw string output produced by the user's code for this test case
        private long executionTimeMs;  // Time taken to execute this test case in milliseconds
        private String errorMessage;   // Any error message (e.g., stack trace snippet), null if none
        private String errorType;      // e.g., "ArithmeticException", "ArrayIndexOutOfBoundsException", null if no error
    }
}
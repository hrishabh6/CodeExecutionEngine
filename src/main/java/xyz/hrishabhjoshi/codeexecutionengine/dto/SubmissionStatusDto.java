package xyz.hrishabhjoshi.codeexecutionengine.dto;

import lombok.*;
import java.util.List;

/**
 * DTO for submission status polling responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionStatusDto {

    /**
     * Unique submission identifier
     */
    private String submissionId;

    /**
     * Current status (QUEUED, COMPILING, RUNNING, COMPLETED, FAILED)
     */
    private String status;

    /**
     * Final verdict (only present when COMPLETED)
     */
    private String verdict;

    /**
     * Runtime in milliseconds (only when completed)
     */
    private Integer runtimeMs;

    /**
     * Memory usage in KB (only when completed)
     */
    private Integer memoryKb;

    /**
     * Error message if any
     */
    private String errorMessage;

    /**
     * Compilation output
     */
    private String compilationOutput;

    /**
     * Test case results
     */
    private List<TestCaseResult> testCaseResults;

    /**
     * Queue position (only when QUEUED)
     */
    private Integer queuePosition;

    /**
     * Timestamp when queued (epoch ms)
     */
    private Long queuedAt;

    /**
     * Timestamp when execution started (epoch ms)
     */
    private Long startedAt;

    /**
     * Timestamp when completed (epoch ms)
     */
    private Long completedAt;

    /**
     * Worker that processed this submission
     */
    private String workerId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseResult {
        private Integer index;
        private Boolean passed;
        private String actualOutput;
        private String expectedOutput;
        private Long executionTimeMs;
        private String error;
        private String errorType;
    }
}

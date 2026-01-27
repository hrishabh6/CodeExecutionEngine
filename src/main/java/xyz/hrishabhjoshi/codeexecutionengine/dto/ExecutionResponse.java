package xyz.hrishabhjoshi.codeexecutionengine.dto;

import lombok.*;

/**
 * Response DTO for immediate submission acknowledgment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResponse {

    /**
     * Unique submission identifier for tracking
     */
    private String submissionId;

    /**
     * Current status (QUEUED initially)
     */
    private String status;

    /**
     * Human-readable message
     */
    private String message;

    /**
     * Position in queue (null if already processing)
     */
    private Integer queuePosition;

    /**
     * Estimated wait time in milliseconds
     */
    private Long estimatedWaitTimeMs;

    /**
     * URL to poll for status
     */
    private String statusUrl;

    /**
     * URL to get full results
     */
    private String resultsUrl;
}

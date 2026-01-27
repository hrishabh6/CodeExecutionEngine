package xyz.hrishabhjoshi.codeexecutionengine.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Represents a code submission with its status, verdict, and performance metrics.
 * This entity persists submission data for polling and history.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
    name = "submission",
    indexes = {
        @Index(name = "idx_submission_id", columnList = "submissionId"),
        @Index(name = "idx_user_status", columnList = "userId, status"),
        @Index(name = "idx_question_status", columnList = "questionId, status"),
        @Index(name = "idx_status_queued", columnList = "status, queuedAt")
    }
)
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique identifier for external reference (UUID)
     */
    @Column(unique = true, nullable = false, length = 36)
    private String submissionId;

    /**
     * User who submitted the code
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * Question being solved
     */
    @Column(nullable = false)
    private Long questionId;

    /**
     * Programming language (java, python, cpp, javascript)
     */
    @Column(nullable = false, length = 20)
    private String language;

    /**
     * User's submitted code
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String code;

    /**
     * Current processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubmissionStatus status;

    /**
     * Final verdict after execution (null until completed)
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private SubmissionVerdict verdict;

    /**
     * Execution runtime in milliseconds
     */
    @Column
    private Integer runtimeMs;

    /**
     * Memory usage in kilobytes
     */
    @Column
    private Integer memoryKb;

    /**
     * Test case results as JSON:
     * [{"index": 0, "passed": true, "time": 15, "output": "..."}, ...]
     */
    @Column(columnDefinition = "JSON")
    private String testResults;

    /**
     * Error message if any
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Compilation output (errors or success message)
     */
    @Column(columnDefinition = "TEXT")
    private String compilationOutput;

    /**
     * Timestamp when submission was queued
     */
    @Column(nullable = false)
    private LocalDateTime queuedAt;

    /**
     * Timestamp when execution started
     */
    @Column
    private LocalDateTime startedAt;

    /**
     * Timestamp when execution completed
     */
    @Column
    private LocalDateTime completedAt;

    /**
     * Client IP address
     */
    @Column(length = 45)
    private String ipAddress;

    /**
     * Client user agent
     */
    @Column(columnDefinition = "TEXT")
    private String userAgent;

    /**
     * Worker ID that processed this submission
     */
    @Column(length = 50)
    private String workerId;

    /**
     * Created timestamp
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last updated timestamp
     */
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (queuedAt == null) {
            queuedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate total processing time from queue to completion
     */
    public Long getProcessingTimeMs() {
        if (queuedAt != null && completedAt != null) {
            return Duration.between(queuedAt, completedAt).toMillis();
        }
        return null;
    }

    /**
     * Calculate time spent waiting in queue
     */
    public Long getQueueWaitTimeMs() {
        if (queuedAt != null && startedAt != null) {
            return Duration.between(queuedAt, startedAt).toMillis();
        }
        return null;
    }
}

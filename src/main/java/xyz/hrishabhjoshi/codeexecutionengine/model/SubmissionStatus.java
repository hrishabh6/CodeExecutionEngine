package xyz.hrishabhjoshi.codeexecutionengine.model;

/**
 * Represents the current processing status of a code submission.
 */
public enum SubmissionStatus {
    /**
     * Submission is queued waiting for a worker
     */
    QUEUED,

    /**
     * Code is being compiled
     */
    COMPILING,

    /**
     * Code is executing against test cases
     */
    RUNNING,

    /**
     * Execution completed (check verdict for result)
     */
    COMPLETED,

    /**
     * System error occurred (not user code error)
     */
    FAILED,

    /**
     * Submission was cancelled by user or timeout
     */
    CANCELLED
}

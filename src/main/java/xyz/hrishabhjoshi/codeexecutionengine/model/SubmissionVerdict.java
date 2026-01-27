package xyz.hrishabhjoshi.codeexecutionengine.model;

/**
 * Represents the final verdict/result of a code submission.
 */
public enum SubmissionVerdict {
    /**
     * All test cases passed
     */
    ACCEPTED,

    /**
     * Output doesn't match expected output
     */
    WRONG_ANSWER,

    /**
     * Execution took too long
     */
    TIME_LIMIT_EXCEEDED,

    /**
     * Used too much memory
     */
    MEMORY_LIMIT_EXCEEDED,

    /**
     * Exception/crash during execution
     */
    RUNTIME_ERROR,

    /**
     * Code failed to compile
     */
    COMPILATION_ERROR,

    /**
     * System/judge error (not user's fault)
     */
    INTERNAL_ERROR
}

package xyz.hrishabhjoshi.codeexecutionengine.dto;

/**
 * Terminal status for PLAYGROUND / raw program execution (not submission judging).
 */
public enum RawExecutionStatus {
    SUCCESS,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}

package xyz.hrishabhjoshi.codeexecutionengine.dto;

/**
 * How CXE should execute an enqueued request. Distinct from deployment property {@code execution.mode}.
 */
public enum ExecutionMode {
    SUBMISSION,
    PLAYGROUND,
    COMPLEXITY_PROFILE
}

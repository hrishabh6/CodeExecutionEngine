package xyz.hrishabhjoshi.codeexecutionengine.core.contract;

/**
 * Raw playground program. This is not a question submission and has no oracle.
 */
public record PlaygroundTaskPayload(String language, String sourceCode, String stdin) {
}

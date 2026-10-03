package xyz.hrishabhjoshi.codeexecutionengine.execution;

import lombok.Getter;

@Getter
public class ExecutionRequestRejectedException extends RuntimeException {

    private final String code;

    public ExecutionRequestRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }
}

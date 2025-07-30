package com.hrishabh.codeexecutionengine.dto;


public class CompilationResult {
    private boolean success;
    private String output;

    public CompilationResult(boolean success, String output) {
        this.success = success;
        this.output = output;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }
}
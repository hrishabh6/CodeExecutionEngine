package xyz.hrishabhjoshi.codeexecutionengine.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.hrishabhjoshi.codeexecutionengine.execution.ExecutionRequestRejectedException;

import java.util.Map;

@RestControllerAdvice
public class ExecutionExceptionHandler {

    @ExceptionHandler(ExecutionRequestRejectedException.class)
    public ResponseEntity<Map<String, String>> handleRejected(ExecutionRequestRejectedException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "code", ex.getCode(),
                "message", ex.getMessage()));
    }
}

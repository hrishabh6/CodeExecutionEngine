package com.hrishabh.codeexecutionengine.service.compilation;

import com.hrishabh.codeexecutionengine.dto.ExecutionResult; // Corrected import to new ExecutionResult DTO
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public interface ExecutionService {
    /**
     * Executes compiled Java code (Main.class) located at the given submissionPath using Docker.
     *
     * @param submissionId     The unique ID for the submission.
     * @param submissionPath   The absolute path to the directory containing the compiled .class files.
     * @param fullyQualifiedMainClass The fully qualified name of the Main class (e.g., "mycode.Main").
     * @param logConsumer      A consumer to handle real-time log lines from Docker during execution.
     * @return An ExecutionResult object containing execution logs and test case outputs.
     * @throws IOException If an I/O error occurs (e.g., Docker not running).
     * @throws InterruptedException If the process is interrupted.
     */
    ExecutionResult run(String submissionId, Path submissionPath, String fullyQualifiedMainClass, Consumer<String> logConsumer) throws IOException, InterruptedException;
}
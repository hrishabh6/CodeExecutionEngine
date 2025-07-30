package com.hrishabh.codeexecutionengine.service.compilation;

import com.hrishabh.codeexecutionengine.dto.CompilationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

public interface CompilationService {
    /**
     * Compiles Java code located at the given submissionPath using Docker.
     *
     * @param submissionId   The unique ID for the submission.
     * @param submissionPath The absolute path to the directory containing Main.java and Solution.java.
     * @param logConsumer    A consumer to handle real-time log lines from Docker during compilation.
     * @return A CompilationResult object indicating success/failure and compilation output.
     * @throws IOException If an I/O error occurs (e.g., Docker not running).
     * @throws InterruptedException If the process is interrupted.
     */
    CompilationResult compile(String submissionId, Path submissionPath, Consumer<String> logConsumer) throws IOException, InterruptedException;
}
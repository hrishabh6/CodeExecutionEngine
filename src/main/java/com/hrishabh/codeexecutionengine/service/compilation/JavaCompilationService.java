package com.hrishabh.codeexecutionengine.service.compilation;

import com.hrishabh.codeexecutionengine.dto.CompilationResult;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.function.Consumer;

@Service
public class JavaCompilationService implements CompilationService {

    private static final String DOCKER_IMAGE = "openjdk:17-jdk-slim";

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public CompilationResult compile(String submissionId, Path submissionPath, Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("COMPILE_SERVICE: Starting compilation for submission: " + submissionId);

        ProcessBuilder compilePb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", submissionPath.toAbsolutePath().toString() + ":/app",
                "-w", "/app", // Working directory inside container
                DOCKER_IMAGE,
                "javac", "-d", "/app", // Compile classes into /app, respecting package structure
                "Main.java", "Solution.java"
        );
        compilePb.redirectErrorStream(true); // Merge stdout and stderr

        Process compileProcess = compilePb.start();
        StringBuilder compileOutputBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                compileOutputBuilder.append(line).append("\n");
                logConsumer.accept("COMPILE_SERVICE_LOG: " + line); // Real-time logging
            }
        }
        int compileExitCode = compileProcess.waitFor();
        String compilationOutput = compileOutputBuilder.toString();

        if (compileExitCode != 0) {
            logConsumer.accept("COMPILE_SERVICE: Compilation failed with exit code: " + compileExitCode);
            return new CompilationResult(false, compilationOutput);
        } else {
            logConsumer.accept("COMPILE_SERVICE: Compilation successful.");
            return new CompilationResult(true, compilationOutput);
        }
    }
}
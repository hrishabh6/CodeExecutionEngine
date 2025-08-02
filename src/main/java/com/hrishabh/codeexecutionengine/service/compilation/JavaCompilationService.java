package com.hrishabh.codeexecutionengine.service.compilation;

import com.hrishabh.codeexecutionengine.dto.CompilationResult;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.function.Consumer;

@Service("javaCompilationService") // Use a specific name for the bean
public class JavaCompilationService implements CompilationService {

    private static final String DOCKER_IMAGE = "openjdk:17-jdk-slim";

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public CompilationResult compile(String submissionId, String fullyQualifiedPackageName, Path submissionPath, Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("COMPILE_SERVICE: Starting compilation for submission: " + submissionId);

        // --- The key change is here ---
        // Dynamically build the path to the source files from the package name
        String packagePath = fullyQualifiedPackageName.replace('.', '/');

        ProcessBuilder compilePb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", submissionPath.toAbsolutePath().toString() + ":/app",
                "-w", "/app", // The working directory is the root of the mount
                DOCKER_IMAGE,
                "javac",
                "-d", "/app", // Correctly compiles classes into /app, respecting package structure
                packagePath + "/Main.java",    // 💡 Correct path to Main.java
                packagePath + "/Solution.java" // 💡 Correct path to Solution.java
        );
        compilePb.redirectErrorStream(true);

        Process compileProcess = compilePb.start();
        StringBuilder compileOutputBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(compileProcess.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                compileOutputBuilder.append(line).append("\n");
                logConsumer.accept("COMPILE_SERVICE_LOG: " + line);
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
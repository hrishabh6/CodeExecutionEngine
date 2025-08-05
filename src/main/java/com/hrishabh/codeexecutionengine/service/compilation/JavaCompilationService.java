package com.hrishabh.codeexecutionengine.service.compilation;

import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.CompilationResult;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.function.Consumer;

@Service("javaCompilationService")
public class JavaCompilationService implements CompilationService {

    private static final String DOCKER_IMAGE = "my-java-runtime:17";

    @Override
    public String getLanguage() {
        return "java";
    }

    @Override
    public CompilationResult compile(String submissionId, String fullyQualifiedPackageName, Path submissionPath, Consumer<String> logConsumer)
            throws IOException, InterruptedException {

        logConsumer.accept("COMPILE_SERVICE: Starting compilation for submission: " + submissionId);

        String packagePath = fullyQualifiedPackageName.replace('.', '/');

        ProcessBuilder compilePb = new ProcessBuilder(
                "docker", "run", "--rm",
                "-v", submissionPath.toAbsolutePath().toString() + ":/app/src", // 💡 Mount to /app/src, not /app
                "-w", "/app", // Working directory remains /app
                DOCKER_IMAGE,
                "javac",
                "-d", "/app/src", // 💡 Output compiled classes to /app/src
                "-cp", "/app/libs/*", // Classpath for Jackson libraries
                "/app/src/" + packagePath + "/Main.java", // 💡 Correct path to the source file
                "/app/src/" + packagePath + "/Solution.java" // 💡 Correct path to the source file
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
package xyz.hrishabhjoshi.codeexecutionengine.core.pipeline;

import xyz.hrishabhjoshi.codeexecutionengine.core.runtime.ExecutionRuntimeSnapshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class CompileCommandPlanner {

    private CompileCommandPlanner() {
    }

    public record JavaCompilePlan(List<String> command, List<String> sourceFiles) {
    }

    public static JavaCompilePlan planJavaCompile(
            ExecutionRuntimeSnapshot runtime,
            Path submissionPath) throws IOException {
        List<String> javaFiles = collectJavaFiles(submissionPath);
        List<String> command = new ArrayList<>();
        command.addAll(runtime.requireLanguage("java").compileCommandTokens());
        command.add("-d");
        command.add(submissionPath.toAbsolutePath().toString());
        command.add("-cp");
        command.add(submissionPath.toAbsolutePath() + File.pathSeparator + runtime.resolvedExtraClasspath());
        command.addAll(javaFiles);
        return new JavaCompilePlan(command, javaFiles);
    }

    public static List<String> collectJavaFiles(Path submissionPath) throws IOException {
        try (Stream<Path> walk = Files.walk(submissionPath)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .map(path -> path.toAbsolutePath().toString())
                    .toList();
        }
    }
}

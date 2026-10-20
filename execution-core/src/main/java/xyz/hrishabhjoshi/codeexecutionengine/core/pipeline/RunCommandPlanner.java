package xyz.hrishabhjoshi.codeexecutionengine.core.pipeline;

import xyz.hrishabhjoshi.codeexecutionengine.core.runtime.ExecutionRuntimeSnapshot;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class RunCommandPlanner {

    private RunCommandPlanner() {
    }

    public static List<String> planJavaRun(
            ExecutionRuntimeSnapshot runtime,
            Path submissionPath,
            String fullyQualifiedMainClass) {
        List<String> command = new ArrayList<>(runtime.requireLanguage("java").runCommandTokens());
        command.add("-cp");
        command.add(submissionPath.toAbsolutePath() + File.pathSeparator + runtime.resolvedExtraClasspath());
        command.add(fullyQualifiedMainClass);
        return command;
    }

    public static List<String> planPythonRun(
            ExecutionRuntimeSnapshot runtime,
            String fullyQualifiedMainClass) {
        List<String> command = new ArrayList<>(runtime.requireLanguage("python").runCommandTokens());
        command.add(constructPythonFilePath(fullyQualifiedMainClass));
        return command;
    }

    public static String constructPythonFilePath(String fullyQualifiedMainClass) {
        if (fullyQualifiedMainClass == null || fullyQualifiedMainClass.trim().isEmpty()) {
            throw new IllegalArgumentException("fullyQualifiedMainClass cannot be null or empty for Python execution");
        }
        String packagePath = fullyQualifiedMainClass;
        if (packagePath.endsWith(".Main")) {
            packagePath = packagePath.substring(0, packagePath.length() - 5);
        }
        return packagePath.replace('.', '/') + "/main.py";
    }
}

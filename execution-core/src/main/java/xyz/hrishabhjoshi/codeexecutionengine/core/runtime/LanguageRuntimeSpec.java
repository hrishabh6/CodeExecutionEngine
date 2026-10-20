package xyz.hrishabhjoshi.codeexecutionengine.core.runtime;

import java.util.List;

public record LanguageRuntimeSpec(String compileCommand, String runCommand) {

    public List<String> compileCommandTokens() {
        return tokenize(compileCommand);
    }

    public List<String> runCommandTokens() {
        return tokenize(runCommand);
    }

    private static List<String> tokenize(String command) {
        if (command == null || command.isBlank()) {
            return List.of();
        }
        return List.of(command.trim().split("\\s+"));
    }
}

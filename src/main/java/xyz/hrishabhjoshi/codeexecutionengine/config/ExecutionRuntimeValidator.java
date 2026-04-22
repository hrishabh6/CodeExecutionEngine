package xyz.hrishabhjoshi.codeexecutionengine.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ExecutionRuntimeValidator {

    private static final Pattern SAFE_COMMAND_PATTERN = Pattern.compile("^[A-Za-z0-9_./:+\\-= ]+$");

    private final ExecutionRuntimeProperties runtimeProperties;

    @PostConstruct
    void validate() {
        validateLanguageRuntime("java", true, true);
        validateLanguageRuntime("python", false, true);
    }

    private void validateLanguageRuntime(String language, boolean requireCompile, boolean requireRun) {
        ExecutionRuntimeProperties.LanguageRuntime runtime = runtimeProperties.getRequiredLanguageRuntime(language);

        if (requireCompile) {
            validateCommand(language, "compile", runtime.getCompile());
        }
        if (requireRun) {
            validateCommand(language, "run", runtime.getRun());
        }
    }

    private void validateCommand(String language, String phase, String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalStateException("Missing " + phase + " command for language: " + language);
        }

        if (!SAFE_COMMAND_PATTERN.matcher(command).matches()) {
            throw new IllegalStateException(
                    "Unsafe characters detected in " + phase + " command for language " + language + ": " + command);
        }
    }
}

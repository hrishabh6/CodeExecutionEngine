package xyz.hrishabhjoshi.codeexecutionengine.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "execution.runtime")
public class ExecutionRuntimeProperties {

    private long compilationTimeoutSeconds = 30;

    private Map<String, LanguageRuntime> languages = new HashMap<>();

    public LanguageRuntime getRequiredLanguageRuntime(String language) {
        LanguageRuntime runtime = languages.get(language.toLowerCase());
        if (runtime == null) {
            throw new IllegalArgumentException("No runtime configuration found for language: " + language);
        }
        return runtime;
    }

    @Getter
    @Setter
    public static class LanguageRuntime {
        private String compile;
        private String run;

        public List<String> compileCommandTokens() {
            return tokenize(compile);
        }

        public List<String> runCommandTokens() {
            return tokenize(run);
        }

        private List<String> tokenize(String command) {
            if (command == null || command.isBlank()) {
                return List.of();
            }
            return List.of(command.trim().split("\\s+"));
        }
    }
}

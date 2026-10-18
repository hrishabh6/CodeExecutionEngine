package xyz.hrishabhjoshi.codeexecutionengine.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
@Setter
@ConfigurationProperties(prefix = "execution.runtime")
public class ExecutionRuntimeProperties {

    private long compilationTimeoutSeconds = 30;

    /**
     * Extra classpath for child javac/java processes.
     * Kubernetes Jobs set this to {@code /app/libs/*}. Those jars are copied
     * into the image; they are not read out of the Spring Boot archive.
     * Local runs may leave this empty and use Jackson jars on the JVM classpath.
     */
    private String extraClasspath = "";

    private Map<String, LanguageRuntime> languages = new HashMap<>();

    /**
     * Concrete jar paths for child javac/java processes. Directory wildcards
     * are expanded here because the child is not started through a shell.
     */
    public String getResolvedExtraClasspath() {
        String resolved = RunnerClasspath.resolve(extraClasspath);
        log.info("[RUNTIME] Resolved runner classpath entries: {}",
                resolved.isEmpty() ? 0 : resolved.split(java.util.regex.Pattern.quote(File.pathSeparator)).length);
        return resolved;
    }

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

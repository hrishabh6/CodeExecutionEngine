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
     * Extra classpath entries for child javac/java processes (e.g. Jackson JARs).
     * - K8s: set via env var EXECUTION_EXTRA_CLASSPATH=/app/libs/*
     * - Local: leave empty → auto-resolved from JVM classpath
     */
    private String extraClasspath = "";

    private Map<String, LanguageRuntime> languages = new HashMap<>();

    /**
     * Returns the extra classpath to append when running child processes.
     * If explicitly set (K8s), uses that value.
     * If empty (local dev), auto-detects Jackson JARs from the current JVM classpath.
     */
    public String getResolvedExtraClasspath() {
        if (extraClasspath != null && !extraClasspath.isBlank()) {
            return extraClasspath;
        }

        // Auto-detect: extract Jackson JAR paths from the running JVM's classpath
        String jvmClasspath = System.getProperty("java.class.path", "");
        StringBuilder jacksonPaths = new StringBuilder();
        for (String entry : jvmClasspath.split(File.pathSeparator)) {
            if (entry.contains("jackson")) {
                if (!jacksonPaths.isEmpty()) {
                    jacksonPaths.append(File.pathSeparator);
                }
                jacksonPaths.append(entry);
            }
        }

        String resolved = jacksonPaths.toString();
        if (!resolved.isEmpty()) {
            log.info("[RUNTIME] Auto-resolved Jackson classpath from JVM: {} entries", 
                    resolved.split(File.pathSeparator).length);
        }
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

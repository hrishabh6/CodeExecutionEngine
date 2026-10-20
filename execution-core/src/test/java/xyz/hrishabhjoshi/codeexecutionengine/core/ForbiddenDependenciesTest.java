package xyz.hrishabhjoshi.codeexecutionengine.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ForbiddenDependenciesTest {

    private static final List<String> FORBIDDEN = List.of(
            "org.springframework",
            "io.fabric8",
            "redis.clients",
            "io.lettuce",
            "jakarta.servlet",
            "org.apache.http");

    @Test
    void executionCoreGradleDoesNotDeclareForbiddenRuntimeDependencies() throws IOException {
        Path gradle = Path.of("build.gradle");
        String text = Files.readString(gradle);
        for (String line : text.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.contains("platform(")) {
                continue;
            }
            if (!trimmed.startsWith("implementation ")
                    && !trimmed.startsWith("api ")
                    && !trimmed.startsWith("runtimeOnly ")) {
                continue;
            }
            for (String forbidden : FORBIDDEN) {
                assertTrue(
                        !trimmed.contains(forbidden),
                        () -> "execution-core dependency line must not reference " + forbidden + ": " + trimmed);
            }
        }
    }

    @Test
    void coreSourcesDoNotImportForbiddenFrameworks() throws IOException {
        Path coreRoot = Path.of("src/main/java");
        try (Stream<Path> files = Files.walk(coreRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            for (String forbidden : FORBIDDEN) {
                                assertTrue(
                                        !source.contains("import " + forbidden),
                                        () -> path + " imports forbidden package " + forbidden);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}

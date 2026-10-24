package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RunnerClasspathTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesOnlyGeneratedHarnessDependenciesFromRunnerDistribution() throws Exception {
        Path annotations = Files.createFile(tempDir.resolve("jackson-annotations-2.19.2.jar"));
        Path core = Files.createFile(tempDir.resolve("jackson-core-2.19.2.jar"));
        Path databind = Files.createFile(tempDir.resolve("jackson-databind-2.19.2.jar"));
        Files.createFile(tempDir.resolve("logback-classic-1.5.18.jar"));
        Files.createFile(tempDir.resolve("lettuce-core-6.5.5.jar"));

        String resolved = RunnerClasspath.resolve(
                tempDir.resolve("*").toString(),
                tempDir.resolve("missing/*").toString(),
                "");

        assertArrayEquals(
                new String[]{annotations.toString(), core.toString(), databind.toString()},
                resolved.split(java.util.regex.Pattern.quote(File.pathSeparator)));
        assertFalse(resolved.contains("logback"));
        assertFalse(resolved.contains("lettuce"));
    }
}

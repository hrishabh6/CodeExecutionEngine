package xyz.hrishabhjoshi.codeexecutionengine.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerClasspathTest {

    @Test
    void expandsDirectoryWildcardToJarFiles(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("jackson-annotations-1.jar"), "a");
        Files.writeString(dir.resolve("jackson-core-1.jar"), "b");
        Files.writeString(dir.resolve("jackson-databind-1.jar"), "c");
        Files.writeString(dir.resolve("notes.txt"), "no");

        String resolved = RunnerClasspath.resolve(dir.toAbsolutePath() + "/*", "/missing/libs/*", "");

        assertTrue(resolved.contains("jackson-databind-1.jar"));
        assertTrue(resolved.contains("jackson-core-1.jar"));
        assertTrue(resolved.contains("jackson-annotations-1.jar"));
        assertTrue(RunnerClasspath.containsRequiredJars(resolved));
    }

    @Test
    void missingImageJarsAreAnInfrastructureFailure(@TempDir Path empty) {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> RunnerClasspath.resolve(empty.toAbsolutePath() + "/*", empty.toAbsolutePath() + "/*", ""));
        assertTrue(failure.getMessage().contains("infrastructure failure"));
        assertEquals(false, failure.getMessage().toLowerCase().contains("cannot find symbol"));
    }
}

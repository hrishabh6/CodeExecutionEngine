package xyz.hrishabhjoshi.codeexecutionengine.core.v2.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class RunnerInstallLocator {

    private RunnerInstallLocator() {
    }

    public static Path repoRoot() {
        return Path.of(System.getProperty("user.dir")).getParent().normalize();
    }

    public static Optional<Path> resolve() {
        Path runner = repoRoot().resolve("nsjail-runner/build/install/nsjail-runner/bin/nsjail-runner");
        if (Files.isExecutable(runner)) {
            return Optional.of(runner);
        }
        return Optional.empty();
    }
}

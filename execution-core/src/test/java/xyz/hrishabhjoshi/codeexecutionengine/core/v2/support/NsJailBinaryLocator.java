package xyz.hrishabhjoshi.codeexecutionengine.core.v2.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class NsJailBinaryLocator {

    private NsJailBinaryLocator() {
    }

    public static Optional<String> resolve() {
        String env = System.getenv("CXE_NSJAIL_BIN");
        if (env != null && !env.isBlank() && Files.isExecutable(Path.of(env.trim()))) {
            return Optional.of(env.trim());
        }
        Path fromModule = RunnerInstallLocator.repoRoot().resolve("../scripts/.local/bin/nsjail").normalize();
        if (Files.isExecutable(fromModule)) {
            return Optional.of(fromModule.toString());
        }
        return Optional.empty();
    }
}

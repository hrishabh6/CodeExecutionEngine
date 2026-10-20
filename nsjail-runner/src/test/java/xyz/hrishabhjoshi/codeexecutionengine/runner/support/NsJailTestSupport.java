package xyz.hrishabhjoshi.codeexecutionengine.runner.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class NsJailTestSupport {

    private NsJailTestSupport() {
    }

    public static Optional<String> resolveNsJailBinary() {
        String env = System.getenv("CXE_NSJAIL_BIN");
        if (env != null && !env.isBlank()) {
            Path path = Path.of(env.trim());
            if (Files.isExecutable(path)) {
                return Optional.of(path.toString());
            }
        }
        Path fromModule = Path.of(System.getProperty("user.dir"))
                .resolve("../../scripts/.local/bin/nsjail")
                .normalize();
        if (Files.isExecutable(fromModule)) {
            return Optional.of(fromModule.toString());
        }
        return Optional.empty();
    }
}

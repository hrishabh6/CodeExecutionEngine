package xyz.hrishabhjoshi.codeexecutionengine.runner.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

public final class WorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceManager.class);

    private final Path workspaceRoot;
    private final Path quarantineRoot;

    public WorkspaceManager(Path workspaceRoot, Path quarantineRoot) {
        this.workspaceRoot = workspaceRoot;
        this.quarantineRoot = quarantineRoot;
    }

    public Path createTaskWorkspace() throws IOException {
        Files.createDirectories(workspaceRoot);
        Path candidate = workspaceRoot.resolve(UUID.randomUUID().toString());
        Files.createDirectories(candidate);
        Path normalized = candidate.toAbsolutePath().normalize();
        if (!normalized.startsWith(workspaceRoot.toAbsolutePath().normalize())) {
            throw new IOException("Workspace path escape rejected");
        }
        if (Files.isSymbolicLink(candidate)) {
            throw new IOException("Symlink workspace rejected");
        }
        return normalized;
    }

    public void deleteOrQuarantine(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }
        try {
            deleteRecursive(workspace);
        } catch (IOException e) {
            log.warn("Workspace delete failed, quarantining {}: {}", workspace, e.getMessage());
            try {
                Files.createDirectories(quarantineRoot);
                Path target = quarantineRoot.resolve(workspace.getFileName().toString() + "-" + System.currentTimeMillis());
                Files.move(workspace, target);
            } catch (IOException moveError) {
                log.error("Quarantine failed for {}", workspace, moveError);
            }
        }
    }

    private static void deleteRecursive(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }
}

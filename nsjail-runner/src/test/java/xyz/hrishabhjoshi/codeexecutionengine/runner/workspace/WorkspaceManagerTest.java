package xyz.hrishabhjoshi.codeexecutionengine.runner.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceManagerTest {

    @Test
    void deletesWorkspaceAfterTask(@TempDir Path root) throws Exception {
        Path workspaces = root.resolve("ws");
        Path quarantine = root.resolve("q");
        WorkspaceManager manager = new WorkspaceManager(workspaces, quarantine);
        Path task = manager.createTaskWorkspace();
        Files.writeString(task.resolve("Hello.java"), "class Hello {}");
        assertTrue(Files.exists(task));
        manager.deleteOrQuarantine(task);
        assertFalse(Files.exists(task));
    }
}

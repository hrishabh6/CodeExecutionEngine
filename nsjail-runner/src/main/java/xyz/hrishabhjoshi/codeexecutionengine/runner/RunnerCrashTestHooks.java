package xyz.hrishabhjoshi.codeexecutionengine.runner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Optional hooks for live SIGKILL recovery tests. Active only when
 * {@code CXE_RUNNER_CRASH_TEST_SYNCPOINT} is set (never in production).
 */
final class RunnerCrashTestHooks {

    private RunnerCrashTestHooks() {
    }

    static boolean singleTaskMode() {
        return truthy(System.getenv("CXE_RUNNER_SINGLE_TASK"));
    }

    static void syncpoint(String name) throws IOException, InterruptedException {
        String expected = System.getenv("CXE_RUNNER_CRASH_TEST_SYNCPOINT");
        if (expected == null || expected.isBlank() || !expected.equals(name)) {
            return;
        }
        String markerPath = System.getenv("CXE_RUNNER_CRASH_TEST_MARKER");
        Path marker = Path.of(
                markerPath == null || markerPath.isBlank() ? "/tmp/cxe-runner-crash.marker" : markerPath);
        Files.createDirectories(marker.getParent() != null ? marker.getParent() : Path.of("."));
        Files.writeString(marker, name + "@" + Instant.now());
        long deadline = System.currentTimeMillis() + 120_000;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
    }

    private static boolean truthy(String value) {
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1"));
    }
}

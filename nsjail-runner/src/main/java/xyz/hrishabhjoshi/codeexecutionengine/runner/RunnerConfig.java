package xyz.hrishabhjoshi.codeexecutionengine.runner;

import xyz.hrishabhjoshi.codeexecutionengine.core.v2.ExecutionV2Keys;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

public final class RunnerConfig {

    private final String runnerId;
    private final String redisHost;
    private final int redisPort;
    private final ExecutionV2Keys keys;
    private final Path workspaceRoot;
    private final Path quarantineRoot;
    private final Duration blockTimeout;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final String nsjailBinary;
    private final int maxOutputBytes;

    private RunnerConfig(
            String runnerId,
            String redisHost,
            int redisPort,
            ExecutionV2Keys keys,
            Path workspaceRoot,
            Path quarantineRoot,
            Duration blockTimeout,
            Duration leaseDuration,
            Duration heartbeatInterval,
            String nsjailBinary,
            int maxOutputBytes) {
        this.runnerId = runnerId;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.keys = keys;
        this.workspaceRoot = workspaceRoot;
        this.quarantineRoot = quarantineRoot;
        this.blockTimeout = blockTimeout;
        this.leaseDuration = leaseDuration;
        this.heartbeatInterval = heartbeatInterval;
        this.nsjailBinary = nsjailBinary;
        this.maxOutputBytes = maxOutputBytes;
    }

    public static RunnerConfig fromEnvironment() {
        String runnerId = env("CXE_RUNNER_ID", "runner-" + UUID.randomUUID());
        String prefix = env("CXE_V2_KEY_PREFIX", "execution:v2:");
        Path workspace = Path.of(env("CXE_RUNNER_WORKSPACE_ROOT", "/tmp/cxe-runner-workspaces"));
        Path quarantine = Path.of(env("CXE_RUNNER_QUARANTINE_ROOT", "/tmp/cxe-runner-quarantine"));
        String nsjail = env("CXE_NSJAIL_BIN", "");
        if (nsjail.isBlank()) {
            throw new IllegalStateException(
                    "CXE_NSJAIL_BIN is required for nsjail-runner; no local-process fallback is permitted");
        }
        return new RunnerConfig(
                runnerId,
                env("CXE_REDIS_HOST", "127.0.0.1"),
                Integer.parseInt(env("CXE_REDIS_PORT", "6379")),
                new ExecutionV2Keys(prefix),
                workspace,
                quarantine,
                Duration.ofSeconds(Long.parseLong(env("CXE_RUNNER_BLOCK_TIMEOUT_SECONDS", "5"))),
                Duration.ofSeconds(Long.parseLong(env("CXE_RUNNER_LEASE_SECONDS", "120"))),
                Duration.ofSeconds(Long.parseLong(env("CXE_RUNNER_HEARTBEAT_SECONDS", "10"))),
                nsjail,
                Integer.parseInt(env("CXE_RUNNER_MAX_OUTPUT_BYTES", "262144")));
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    public String runnerId() {
        return runnerId;
    }

    public String redisHost() {
        return redisHost;
    }

    public int redisPort() {
        return redisPort;
    }

    public ExecutionV2Keys keys() {
        return keys;
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public Path quarantineRoot() {
        return quarantineRoot;
    }

    public Duration blockTimeout() {
        return blockTimeout;
    }

    public Duration leaseDuration() {
        return leaseDuration;
    }

    public Duration heartbeatInterval() {
        return heartbeatInterval;
    }

    public String nsjailBinary() {
        return nsjailBinary;
    }

    public int maxOutputBytes() {
        return maxOutputBytes;
    }

    public String redisUri() {
        return String.format(Locale.ROOT, "redis://%s:%d", redisHost, redisPort);
    }
}

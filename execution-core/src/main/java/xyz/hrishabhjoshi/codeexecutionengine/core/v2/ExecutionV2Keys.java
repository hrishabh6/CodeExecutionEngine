package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

public final class ExecutionV2Keys {

    private final String prefix;

    public ExecutionV2Keys(String prefix) {
        this.prefix = prefix.endsWith(":") ? prefix : prefix + ":";
    }

    public String readyList() {
        return prefix + "ready";
    }

    public String processingList() {
        return prefix + "processing";
    }

    public String taskKey(String executionId) {
        return prefix + "task:" + executionId;
    }

    public String claimKey(String executionId) {
        return prefix + "claim:" + executionId;
    }

    public String statusKey(String submissionId) {
        return prefix + "status:" + submissionId;
    }

    public String resultKey(String executionId) {
        return prefix + "result:" + executionId;
    }

    public String queuedZset() {
        return prefix + "queued";
    }

    public String deadLetterKey(String executionId) {
        return prefix + "dead-letter:" + executionId;
    }

    public String runnerHeartbeatKey(String runnerId) {
        return prefix + "runner:" + runnerId + ":heartbeat";
    }

    public String reaperLockKey() {
        return prefix + "reaper-lock";
    }

    public String prefix() {
        return prefix;
    }
}

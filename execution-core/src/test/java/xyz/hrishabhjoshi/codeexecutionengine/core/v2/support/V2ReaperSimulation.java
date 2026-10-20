package xyz.hrishabhjoshi.codeexecutionengine.core.v2.support;

import io.lettuce.core.api.sync.RedisCommands;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.ExecutionV2Keys;

import java.util.List;

/** Mirrors {@code ExecutionV2Reaper} reconciliation for integration tests. */
public final class V2ReaperSimulation {

    private V2ReaperSimulation() {
    }

    public static void reconcile(RedisCommands<String, String> sync, ExecutionV2Keys keys, long nowEpochSeconds) {
        List<String> processing = sync.lrange(keys.processingList(), 0, -1);
        if (processing == null) {
            return;
        }
        for (String executionId : processing) {
            String claim = sync.get(keys.claimKey(executionId));
            if (claim == null) {
                if (sync.exists(keys.taskKey(executionId)) > 0) {
                    sync.lrem(keys.processingList(), 1, executionId);
                    sync.lpush(keys.readyList(), executionId);
                }
                continue;
            }
            String[] parts = claim.split("\\|", 4);
            if (parts.length == 4) {
                long expiry = Long.parseLong(parts[3]);
                if (expiry < nowEpochSeconds) {
                    sync.lrem(keys.processingList(), 1, executionId);
                    sync.lpush(keys.readyList(), executionId);
                }
            }
        }
    }
}

package xyz.hrishabhjoshi.codeexecutionengine.core.v2.support;

import io.lettuce.core.RedisClient;
import io.lettuce.core.LMoveArgs;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.ExecutionV2Keys;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.LuaScriptLoader;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.V2ClaimRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class V2RedisTestClient implements AutoCloseable {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> sync;
    private final ExecutionV2Keys keys;
    private final String enqueueSha;
    private final String claimSha;
    private final String heartbeatSha;
    private final String completeSha;

    public V2RedisTestClient(String redisUri, ExecutionV2Keys keys) {
        this.client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.sync = connection.sync();
        this.keys = keys;
        this.enqueueSha = sync.scriptLoad(LuaScriptLoader.enqueueScript());
        this.claimSha = sync.scriptLoad(LuaScriptLoader.claimFinalizeScript());
        this.heartbeatSha = sync.scriptLoad(LuaScriptLoader.heartbeatScript());
        this.completeSha = sync.scriptLoad(LuaScriptLoader.completeScript());
    }

    public ExecutionV2Keys keys() {
        return keys;
    }

    public List<Object> enqueue(
            String executionId,
            String taskJson,
            String statusJson,
            String contentHash,
            int capacity,
            long taskTtlSeconds,
            long statusTtlSeconds) {
        return sync.evalsha(
                enqueueSha,
                ScriptOutputType.MULTI,
                new String[] {
                        keys.readyList(),
                        keys.processingList(),
                        keys.taskKey(executionId),
                        keys.statusKey(extractSubmissionId(statusJson)),
                        keys.queuedZset()
                },
                executionId,
                taskJson,
                statusJson,
                contentHash,
                String.valueOf(capacity),
                String.valueOf(taskTtlSeconds),
                String.valueOf(statusTtlSeconds),
                String.valueOf(System.currentTimeMillis()),
                contentHash);
    }

    public Optional<String> popReadyToProcessing(long timeoutSeconds) {
        String executionId = sync.blmove(
                keys.readyList(), keys.processingList(), LMoveArgs.Builder.rightLeft(), timeoutSeconds);
        if (executionId == null || executionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(executionId);
    }

    public Optional<String> moveReadyToProcessing() {
        return Optional.ofNullable(sync.lmove(
                keys.readyList(), keys.processingList(), LMoveArgs.Builder.rightLeft()));
    }

    public List<String> readyEntries() {
        return sync.lrange(keys.readyList(), 0, -1);
    }

    public List<String> processingEntries() {
        return sync.lrange(keys.processingList(), 0, -1);
    }

    public List<Object> finalizeClaim(
            String executionId,
            String submissionId,
            String runnerId,
            String attemptToken,
            long attemptGeneration,
            String processingStatusJson,
            long ttlSeconds) {
        long leaseExpiry = Instant.now().getEpochSecond() + ttlSeconds;
        return sync.evalsha(
                claimSha,
                ScriptOutputType.MULTI,
                new String[] {
                        keys.taskKey(executionId),
                        keys.claimKey(executionId),
                        keys.statusKey(submissionId),
                        keys.queuedZset()
                },
                executionId,
                runnerId,
                attemptToken,
                String.valueOf(attemptGeneration),
                String.valueOf(leaseExpiry),
                processingStatusJson,
                String.valueOf(ttlSeconds));
    }

    public List<Object> heartbeat(String executionId, V2ClaimRecord claim, long ttlSeconds) {
        long leaseExpiry = Instant.now().getEpochSecond() + ttlSeconds;
        return sync.evalsha(
                heartbeatSha,
                ScriptOutputType.MULTI,
                new String[] {keys.claimKey(executionId)},
                claim.runnerId(),
                claim.attemptToken(),
                String.valueOf(leaseExpiry),
                String.valueOf(ttlSeconds));
    }

    public List<Object> complete(
            String executionId,
            String submissionId,
            String attemptToken,
            String resultJson,
            String statusJson,
            long ttlSeconds) {
        return sync.evalsha(
                completeSha,
                ScriptOutputType.MULTI,
                new String[] {
                        keys.taskKey(executionId),
                        keys.claimKey(executionId),
                        keys.statusKey(submissionId),
                        keys.resultKey(executionId),
                        keys.processingList()
                },
                executionId,
                attemptToken,
                resultJson,
                statusJson,
                String.valueOf(ttlSeconds),
                String.valueOf(ttlSeconds));
    }

    public String get(String key) {
        return sync.get(key);
    }

    public void setClaim(String executionId, String rawClaim, long ttlSeconds) {
        sync.setex(keys.claimKey(executionId), ttlSeconds, rawClaim);
    }

    public long processingLength() {
        return sync.llen(keys.processingList());
    }

    public long readyLength() {
        return sync.llen(keys.readyList());
    }

    public void simulateReaper(long nowEpochSeconds) {
        V2ReaperSimulation.reconcile(sync, keys, nowEpochSeconds);
    }

    private static String extractSubmissionId(String statusJson) {
        int idx = statusJson.indexOf("\"submissionId\"");
        if (idx < 0) {
            return "unknown";
        }
        int start = statusJson.indexOf('"', idx + 15);
        int end = statusJson.indexOf('"', start + 1);
        if (start < 0 || end < 0) {
            return "unknown";
        }
        return statusJson.substring(start + 1, end);
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }
}

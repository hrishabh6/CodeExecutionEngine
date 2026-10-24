package xyz.hrishabhjoshi.codeexecutionengine.runner.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.ExecutionV2Keys;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.LuaScriptLoader;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.V2ClaimRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class RedisV2RunnerClient implements AutoCloseable {

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> sync;
    private final ExecutionV2Keys keys;
    private final String claimScriptSource = LuaScriptLoader.claimFinalizeScript();
    private final String heartbeatScriptSource = LuaScriptLoader.heartbeatScript();
    private final String completeScriptSource = LuaScriptLoader.completeScript();
    private String claimScript;
    private String heartbeatScript;
    private String completeScript;

    public RedisV2RunnerClient(String redisUri, ExecutionV2Keys keys) {
        this.client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.sync = connection.sync();
        this.keys = keys;
        this.claimScript = sync.scriptLoad(claimScriptSource);
        this.heartbeatScript = sync.scriptLoad(heartbeatScriptSource);
        this.completeScript = sync.scriptLoad(completeScriptSource);
    }

    public Optional<String> blockClaimReady(long timeoutSeconds) {
        io.lettuce.core.KeyValue<String, String> popped = sync.brpop(timeoutSeconds, keys.readyList());
        if (popped == null || popped.hasValue() == false || popped.getValue() == null || popped.getValue().isBlank()) {
            return Optional.empty();
        }
        String executionId = popped.getValue();
        sync.lpush(keys.processingList(), executionId);
        return Optional.of(executionId);
    }

    public boolean finalizeClaim(
            String executionId,
            String submissionId,
            String runnerId,
            String attemptToken,
            long attemptGeneration,
            String processingStatusJson,
            long ttlSeconds) {
        long leaseExpiry = Instant.now().getEpochSecond() + ttlSeconds;
        List<Object> result = evalClaim(
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
        return result != null && Long.parseLong(String.valueOf(result.getFirst())) == 1L;
    }

    public boolean heartbeat(String executionId, V2ClaimRecord claim, long ttlSeconds) {
        long leaseExpiry = Instant.now().getEpochSecond() + ttlSeconds;
        List<Object> result = evalHeartbeat(
                new String[] {keys.claimKey(executionId)},
                claim.runnerId(),
                claim.attemptToken(),
                String.valueOf(leaseExpiry),
                String.valueOf(ttlSeconds));
        return result != null && Long.parseLong(String.valueOf(result.getFirst())) == 1L;
    }

    public String getTaskJson(String executionId) {
        return sync.get(keys.taskKey(executionId));
    }

    public CommitResult complete(
            String executionId,
            String submissionId,
            String attemptToken,
            String resultJson,
            String statusJson,
            long ttlSeconds) {
        List<Object> result = evalComplete(
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
        if (result == null || result.size() < 2) {
            return CommitResult.FAILED;
        }
        long code = Long.parseLong(String.valueOf(result.get(0)));
        String label = String.valueOf(result.get(1));
        if (code == 1L) {
            return CommitResult.COMMITTED;
        }
        if ("STALE_TOKEN".equals(label)) {
            return CommitResult.STALE;
        }
        return CommitResult.FAILED;
    }

    public void publishHeartbeat(String runnerId, String payload, long ttlSeconds) {
        sync.setex(keys.runnerHeartbeatKey(runnerId), ttlSeconds, payload);
    }

    private List<Object> evalClaim(String[] keys, String... values) {
        try {
            return sync.evalsha(claimScript, io.lettuce.core.ScriptOutputType.MULTI, keys, values);
        } catch (RedisNoScriptException missing) {
            claimScript = sync.scriptLoad(claimScriptSource);
            return sync.evalsha(claimScript, io.lettuce.core.ScriptOutputType.MULTI, keys, values);
        }
    }

    private List<Object> evalHeartbeat(String[] keys, String... values) {
        try {
            return sync.evalsha(heartbeatScript, io.lettuce.core.ScriptOutputType.MULTI, keys, values);
        } catch (RedisNoScriptException missing) {
            heartbeatScript = sync.scriptLoad(heartbeatScriptSource);
            return sync.evalsha(heartbeatScript, io.lettuce.core.ScriptOutputType.MULTI, keys, values);
        }
    }

    private List<Object> evalComplete(String[] keys, String... values) {
        try {
            return sync.evalsha(completeScript, io.lettuce.core.ScriptOutputType.MULTI, keys, values);
        } catch (RedisNoScriptException missing) {
            completeScript = sync.scriptLoad(completeScriptSource);
            return sync.evalsha(completeScript, io.lettuce.core.ScriptOutputType.MULTI, keys, values);
        }
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

    public enum CommitResult {
        COMMITTED,
        STALE,
        FAILED
    }
}

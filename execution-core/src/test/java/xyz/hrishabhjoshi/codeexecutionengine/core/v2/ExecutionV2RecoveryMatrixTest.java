package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.support.RedisV2TestBase;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExecutionV2RecoveryMatrixTest extends RedisV2TestBase {

    @Test
    void staleAttemptTokenCannotCommitTerminalResult() {
        seedClaimedTask("exec-stale", "sub-stale", "runner-a", "token-good", 120);

        String resultJson = "{\"terminalCategory\":\"SUCCESS\"}";
        List<Object> stale = client.complete(
                "exec-stale", "sub-stale", "token-wrong", resultJson, statusJson("sub-stale", "exec-stale", "DONE"), 3600);
        assertEquals(0L, Long.parseLong(String.valueOf(stale.get(0))));
        assertEquals("STALE_TOKEN", String.valueOf(stale.get(1)));
        assertNull(client.get(keys.resultKey("exec-stale")));
    }

    @Test
    void terminalCommitIsIdempotent() {
        seedClaimedTask("exec-idem-commit", "sub-idem-commit", "runner-a", "token-a", 120);
        String resultJson = "{\"terminalCategory\":\"SUCCESS\",\"payload\":\"same\"}";
        String statusJson = statusJson("sub-idem-commit", "exec-idem-commit", "COMPLETED");

        List<Object> first = client.complete(
                "exec-idem-commit", "sub-idem-commit", "token-a", resultJson, statusJson, 3600);
        List<Object> second = client.complete(
                "exec-idem-commit", "sub-idem-commit", "token-a", resultJson, statusJson, 3600);

        assertEquals("COMMITTED", String.valueOf(first.get(1)));
        assertEquals(1L, Long.parseLong(String.valueOf(second.get(0))));
        assertEquals("IDEMPOTENT", String.valueOf(second.get(1)));
    }

    @Test
    void conflictingTerminalResultIsRejected() {
        seedClaimedTask("exec-conflict", "sub-conflict", "runner-a", "token-a", 120);
        client.complete(
                "exec-conflict",
                "sub-conflict",
                "token-a",
                "{\"terminalCategory\":\"SUCCESS\",\"payload\":\"first\"}",
                statusJson("sub-conflict", "exec-conflict", "COMPLETED"),
                3600);

        List<Object> conflict = client.complete(
                "exec-conflict",
                "sub-conflict",
                "token-a",
                "{\"terminalCategory\":\"SUCCESS\",\"payload\":\"second\"}",
                statusJson("sub-conflict", "exec-conflict", "COMPLETED"),
                3600);
        assertEquals(0L, Long.parseLong(String.valueOf(conflict.get(0))));
        assertEquals("CONFLICTING_TERMINAL", String.valueOf(conflict.get(1)));
    }

    @Test
    void expiredLeaseIsRequeuedByReaper() {
        String executionId = "exec-lease";
        String submissionId = "sub-lease";
        client.enqueue(
                executionId,
                "{\"executionId\":\"" + executionId + "\"}",
                statusJson(submissionId, executionId, "QUEUED"),
                "hash-lease",
                10,
                3600,
                3600);
        client.popReadyToProcessing(2);
        client.finalizeClaim(
                executionId,
                submissionId,
                "runner-a",
                "token-a",
                1L,
                statusJson(submissionId, executionId, "PROCESSING"),
                120);

        long expired = Instant.now().getEpochSecond() - 10;
        client.setClaim(executionId, new V2ClaimRecord("runner-a", "token-a", 1L, expired).encode(), 120);

        client.simulateReaper(Instant.now().getEpochSecond());
        assertEquals(1L, client.readyLength());
        assertEquals(0L, client.processingLength());
        assertNotNull(client.get(keys.taskKey(executionId)));
    }

    @Test
    void orphanedProcessingEntryWithoutClaimIsRequeued() {
        String executionId = "exec-orphan";
        client.enqueue(
                executionId,
                "{\"executionId\":\"" + executionId + "\"}",
                statusJson("sub-orphan", executionId, "QUEUED"),
                "hash-orphan",
                10,
                3600,
                3600);
        client.popReadyToProcessing(2);

        client.simulateReaper(Instant.now().getEpochSecond());
        assertEquals(1L, client.readyLength());
        assertNotNull(client.get(keys.taskKey(executionId)));
    }

    @Test
    void staleHeartbeatRejectedForWrongRunner() {
        seedClaimedTask("exec-hb", "sub-hb", "runner-a", "token-a", 120);
        V2ClaimRecord wrongRunner = new V2ClaimRecord("runner-b", "token-a", 1L, Instant.now().getEpochSecond() + 60);
        List<Object> heartbeat = client.heartbeat("exec-hb", wrongRunner, 120);
        assertEquals(0L, Long.parseLong(String.valueOf(heartbeat.get(0))));
        assertEquals("STALE", String.valueOf(heartbeat.get(1)));
    }

    private void seedClaimedTask(
            String executionId, String submissionId, String runnerId, String token, long ttlSeconds) {
        client.enqueue(
                executionId,
                "{\"executionId\":\"" + executionId + "\"}",
                statusJson(submissionId, executionId, "QUEUED"),
                "hash-" + executionId,
                10,
                3600,
                3600);
        client.popReadyToProcessing(2);
        client.finalizeClaim(
                executionId,
                submissionId,
                runnerId,
                token,
                1L,
                statusJson(submissionId, executionId, "PROCESSING"),
                ttlSeconds);
    }
}

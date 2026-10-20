package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.support.RedisV2TestBase;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionV2RedisIntegrationTest extends RedisV2TestBase {

    @Test
    void enqueueIsIdempotentForSameContentHash() {
        String executionId = "exec-idem";
        String submissionId = "sub-idem";
        String taskJson = "{\"executionId\":\"" + executionId + "\"}";
        String statusJson = statusJson(submissionId, executionId, "QUEUED");
        String hash = "abc123";

        List<Object> first = client.enqueue(executionId, taskJson, statusJson, hash, 10, 3600, 3600);
        List<Object> second = client.enqueue(executionId, taskJson, statusJson, hash, 10, 3600, 3600);

        assertEquals(1L, Long.parseLong(String.valueOf(first.get(0))));
        assertEquals("ENQUEUED", String.valueOf(first.get(1)));
        assertEquals(1L, Long.parseLong(String.valueOf(second.get(0))));
        assertEquals("IDEMPOTENT", String.valueOf(second.get(1)));
        assertEquals(1L, client.readyLength());
    }

    @Test
    void enqueueRejectsQueueFull() {
        String hash1 = "h1";
        String hash2 = "h2";
        client.enqueue("e1", "{}", statusJson("s1", "e1", "QUEUED"), hash1, 1, 3600, 3600);
        List<Object> second = client.enqueue("e2", "{}", statusJson("s2", "e2", "QUEUED"), hash2, 1, 3600, 3600);
        assertEquals(0L, Long.parseLong(String.valueOf(second.get(0))));
        assertEquals("QUEUE_FULL", String.valueOf(second.get(1)));
    }

    @Test
    void claimFinalizeHeartbeatAndCompleteRoundTrip() {
        String executionId = "exec-round";
        String submissionId = "sub-round";
        String taskJson = "{\"executionId\":\"" + executionId + "\",\"payload\":\"x\"}";
        String queuedStatus = statusJson(submissionId, executionId, "QUEUED");
        client.enqueue(executionId, taskJson, queuedStatus, "hash-round", 10, 3600, 3600);

        String popped = client.popReadyToProcessing(2).orElseThrow();
        assertEquals(executionId, popped);

        String processingStatus = statusJson(submissionId, executionId, "PROCESSING");
        List<Object> claim = client.finalizeClaim(
                executionId, submissionId, "runner-a", "token-a", 1L, processingStatus, 120);
        assertEquals(1L, Long.parseLong(String.valueOf(claim.get(0))));
        assertEquals("CLAIMED", String.valueOf(claim.get(1)));

        V2ClaimRecord record = V2ClaimRecord.decode(client.get(keys.claimKey(executionId)));
        List<Object> heartbeat = client.heartbeat(executionId, record, 120);
        assertEquals(1L, Long.parseLong(String.valueOf(heartbeat.get(0))));

        String resultJson = "{\"terminalCategory\":\"SUCCESS\",\"result\":{\"submissionId\":\"" + submissionId + "\"}}";
        String terminalStatus = statusJson(submissionId, executionId, "COMPLETED");
        List<Object> commit = client.complete(executionId, submissionId, "token-a", resultJson, terminalStatus, 3600);
        assertEquals(1L, Long.parseLong(String.valueOf(commit.get(0))));
        assertEquals("COMMITTED", String.valueOf(commit.get(1)));
        assertNotNull(client.get(keys.resultKey(executionId)));
        assertEquals(0L, client.processingLength());
    }
}

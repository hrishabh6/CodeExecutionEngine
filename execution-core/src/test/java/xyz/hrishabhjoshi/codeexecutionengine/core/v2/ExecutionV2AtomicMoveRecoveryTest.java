package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.support.RedisV2TestBase;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionV2AtomicMoveRecoveryTest extends RedisV2TestBase {

    @Test
    void crashBeforeMoveLeavesTaskReadyAndRecoverable() {
        enqueue("before-move", "sub-before");

        assertEquals(List.of("before-move"), client.readyEntries());
        assertTrue(client.processingEntries().isEmpty());
        assertTrue(client.get(keys.taskKey("before-move")) != null);
    }

    @Test
    void crashAfterAtomicMoveIsRecoveredFromProcessing() {
        enqueue("after-move", "sub-after");

        assertEquals("after-move", client.moveReadyToProcessing().orElseThrow());
        assertTrue(client.readyEntries().isEmpty());
        assertEquals(List.of("after-move"), client.processingEntries());

        client.simulateReaper(Instant.now().getEpochSecond());
        assertEquals(List.of("after-move"), client.readyEntries());
        assertTrue(client.processingEntries().isEmpty());
        assertTrue(client.get(keys.taskKey("after-move")) != null);
    }

    @Test
    void duplicateClaimersCannotMoveSameTaskTwice() {
        enqueue("single-owner", "sub-owner");

        assertEquals("single-owner", client.moveReadyToProcessing().orElseThrow());
        assertTrue(client.moveReadyToProcessing().isEmpty());
        assertTrue(client.readyEntries().isEmpty());
        assertEquals(List.of("single-owner"), client.processingEntries());
    }

    private void enqueue(String executionId, String submissionId) {
        List<Object> result = client.enqueue(
                executionId,
                "{\"executionId\":\"" + executionId + "\"}",
                statusJson(submissionId, executionId, "QUEUED"),
                "hash-" + executionId,
                10,
                3600,
                3600);
        assertEquals(1L, Long.parseLong(String.valueOf(result.getFirst())));
    }
}

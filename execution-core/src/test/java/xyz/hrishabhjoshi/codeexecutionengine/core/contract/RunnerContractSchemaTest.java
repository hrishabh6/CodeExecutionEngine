package xyz.hrishabhjoshi.codeexecutionengine.core.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerContractSchemaTest {

    @Test
    void envelopeRoundTripPreservesContractVersion() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RunnerTaskEnvelope envelope = new RunnerTaskEnvelope(
                ExecutionContractVersion.V2_RUNNER_QUEUE,
                "task-1",
                "attempt-1",
                "sub-1",
                "exec-1",
                "SUBMISSION",
                "java",
                "c3Vi",
                Instant.parse("2026-10-20T00:00:00Z"),
                "default");
        String json = mapper.writeValueAsString(envelope);
        RunnerTaskEnvelope decoded = mapper.readValue(json, RunnerTaskEnvelope.class);
        assertEquals(ExecutionContractVersion.V2_RUNNER_QUEUE, decoded.contractVersion());
        assertEquals("task-1", decoded.taskId());
    }

    @Test
    void runnerTaskEnvelopeSchemaIsPinned() throws Exception {
        Path schema = Path.of("src/test/resources/schemas/runner-task-envelope.schema.json");
        byte[] bytes = Files.readAllBytes(schema);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String hash = HexFormat.of().formatHex(digest.digest(bytes));
        assertTrue(hash.length() == 64);
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("cxe.runner.v2"));
    }
}

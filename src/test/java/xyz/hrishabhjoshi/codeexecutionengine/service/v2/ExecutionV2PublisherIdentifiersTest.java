package xyz.hrishabhjoshi.codeexecutionengine.service.v2;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExecutionV2PublisherIdentifiersTest {

    @Test
    void generatesMissingSubmissionAndExecutionIdentifiers() {
        ExecutionRequest request = new ExecutionRequest();

        ExecutionV2Publisher.ensureIdentifiers(request);

        assertFalse(request.getSubmissionId().isBlank());
        assertFalse(request.getExecutionId().isBlank());
    }

    @Test
    void preservesCallerIdentifiers() {
        ExecutionRequest request = new ExecutionRequest();
        request.setSubmissionId("submission-1");
        request.setExecutionId("execution-1");

        ExecutionV2Publisher.ensureIdentifiers(request);

        assertEquals("submission-1", request.getSubmissionId());
        assertEquals("execution-1", request.getExecutionId());
    }
}

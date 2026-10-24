package xyz.hrishabhjoshi.codeexecutionengine.core.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import xyz.hrishabhjoshi.codeexecutionengine.core.codec.ExecutionPayloadCodecCore;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.ExecutionContractVersion;
import xyz.hrishabhjoshi.codeexecutionengine.core.contract.RunnerTaskEnvelope;
import xyz.hrishabhjoshi.codeexecutionengine.core.v2.support.V2RedisTestClient;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minikube disposable-namespace smoke test. Requires port-forward to disposable Redis, e.g.:
 * kubectl port-forward -n algocrack-phase5-disposable svc/redis 6388:6379
 * PHASE5_REDIS_URI=redis://127.0.0.1:6389 ./gradlew :execution-core:test --tests Phase5DisposableV2SmokeTest
 */
class Phase5DisposableV2SmokeTest {

    private static V2RedisTestClient client;
    private static ExecutionV2Keys keys;
    private static String executionId;

    @BeforeAll
    static void connect() {
        String uri = System.getenv("PHASE5_REDIS_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank(), "PHASE5_REDIS_URI required");
        keys = new ExecutionV2Keys("execution:v2:phase5-disposable:");
        client = new V2RedisTestClient(uri.trim(), keys);
        executionId = "phase5-exec-" + UUID.randomUUID();
    }

    @Test
    void runnerConsumesTaskAndWritesTerminalResult() throws Exception {
        CodeSubmissionDTO submission = addSubmission(executionId);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = new ExecutionPayloadCodecCore(mapper).encode(submission);
        RunnerTaskEnvelope envelope = new RunnerTaskEnvelope(
                ExecutionContractVersion.V2_RUNNER_QUEUE,
                executionId,
                "0",
                submission.getSubmissionId(),
                executionId,
                "RUN",
                "java",
                payload,
                Instant.now().plusSeconds(3600),
                "default");
        String taskJson = mapper.writeValueAsString(envelope);
        String statusJson = "{\"submissionId\":\"" + submission.getSubmissionId()
                + "\",\"executionId\":\"" + executionId + "\",\"status\":\"QUEUED\"}";
        String hash = UUID.randomUUID().toString().replace("-", "");
        client.enqueue(executionId, taskJson, statusJson, hash, 32, 3600, 3600);

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(180);
        String result = null;
        while (System.currentTimeMillis() < deadline) {
            result = client.get(keys.resultKey(executionId));
            if (result != null && !result.isBlank()) {
                break;
            }
            Thread.sleep(500);
        }
        assertNotNull(result, "terminal result must appear after runner processes task");
        assertTrue(
                result.contains("\"terminalCategory\":\"SUCCESS\"")
                        || result.contains("\"overallStatus\":\"SUCCESS\""),
                result);
    }

    private static CodeSubmissionDTO addSubmission(String executionId) {
        CodeSubmissionDTO.QuestionMetadata metadata = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName("q.add")
                .functionName("add")
                .returnType("int")
                .questionType("ALGORITHM")
                .parameters(List.of(new ParamInfoDTO("a", "int"), new ParamInfoDTO("b", "int")))
                .build();
        return CodeSubmissionDTO.builder()
                .submissionId("sub-phase5-" + executionId)
                .executionId(executionId)
                .language("java")
                .userSolutionCode("class Solution { public int add(int a, int b) { return a + b; } }")
                .questionMetadata(metadata)
                .testCases(List.of(Map.of("input", Map.of("a", 2, "b", 3))))
                .build();
    }
}

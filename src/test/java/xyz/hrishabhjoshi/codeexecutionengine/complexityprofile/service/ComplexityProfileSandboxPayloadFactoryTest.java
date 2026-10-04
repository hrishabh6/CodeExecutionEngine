package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ParameterDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileSandboxDtos.ComplexityProfileSandboxJobPayload;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ComplexityProfileSandboxPayloadFactoryTest {

    private final ComplexityProfileSandboxPayloadFactory factory = new ComplexityProfileSandboxPayloadFactory();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sandboxPayloadOmitsExpectedOutput() throws Exception {
        var expected = mapper.valueToTree(42);
        ProfileCaseRequest profileCase = new ProfileCaseRequest(
                "c1", "id1", "P", "v1", "hash", "gv1", "RANDOM", Map.of("n", 1), "seed",
                mapper.createArrayNode().add(mapper.createArrayNode().add(1)),
                "ih", expected, 0, 2);
        ComplexityProfileJobPayload job = ComplexityProfileJobPayload.builder()
                .executionId("exec-1")
                .submissionId("sub")
                .questionId(1L)
                .language("JAVA")
                .sourceCode("class Solution {}")
                .questionMetadata(new QuestionMetadataDto("", "sum", "int", List.of(new ParameterDto("a", "int[]")), Map.of(), "ALGORITHM", null))
                .profileCode("P")
                .profileVersion("v1")
                .profileHash("hash")
                .harnessVersion("v1")
                .cases(List.of(profileCase))
                .build();

        ComplexityProfileSandboxJobPayload sandbox = factory.toSandboxJobPayload(job);
        String json = mapper.writeValueAsString(sandbox);
        assertFalse(json.contains("expectedOutput"));
    }
}

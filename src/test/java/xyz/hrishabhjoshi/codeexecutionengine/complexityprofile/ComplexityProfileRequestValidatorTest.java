package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ComplexityProfileSubmitRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ParameterDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.service.ComplexityProfileRequestValidator;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.execution.ExecutionRequestRejectedException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComplexityProfileRequestValidatorTest {

    private ComplexityProfileRequestValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(true);
        properties.setHarnessVersion("v1");
        validator = new ComplexityProfileRequestValidator(properties);
        objectMapper = new ObjectMapper();
    }

    @Test
    void rejectsSampleCeilingViolations() {
        ComplexityProfileSubmitRequest request = baseRequest(11, 21);
        assertThrows(ExecutionRequestRejectedException.class, () -> validator.validate(request));
    }

    @Test
    void acceptsBoundedSamplePlan() {
        assertDoesNotThrow(() -> validator.validate(baseRequest(3, 5)));
    }

    @Test
    void rejectsKubernetesBackendWithoutProductionVerified() {
        ComplexityProfileExecutionProperties properties = new ComplexityProfileExecutionProperties();
        properties.setEnabled(true);
        properties.setHarnessVersion("v1");
        properties.getSandbox().setBackend("kubernetes-job");
        properties.getSandbox().setProductionVerified(false);
        ComplexityProfileRequestValidator k8sValidator = new ComplexityProfileRequestValidator(properties);
        assertThrows(ExecutionRequestRejectedException.class, () -> k8sValidator.validate(baseRequest(1, 2)));
    }

    private ComplexityProfileSubmitRequest baseRequest(int warmups, int measured) {
        ArrayNode input = objectMapper.createArrayNode().add(objectMapper.createArrayNode().add(1));
        var expected = objectMapper.valueToTree(42);
        ProfileCaseRequest profileCase = new ProfileCaseRequest(
                "c1", "id1", "P", "v1", "hash", "gv1", "RANDOM", Map.of("n", 1), "seed",
                input, "ih", expected, warmups, measured);
        return new ComplexityProfileSubmitRequest(
                "exec-idempotent-1",
                "sub",
                1L,
                "JAVA",
                "class Solution { public int sum(int[] a){ return 42; } }",
                new QuestionMetadataDto("", "sum", "int", List.of(new ParameterDto("a", "int[]")), Map.of(), "ALGORITHM", null),
                "P",
                "v1",
                "hash",
                "v1",
                List.of(profileCase));
    }
}

package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ParameterDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ProfileCaseRequest;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileJobPayload;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness.ComplexityProfileChildJvmRunner;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness.ComplexityProfileJavaHarness;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness.ComplexityProfileJavaHarness.HarnessRunResult;
import xyz.hrishabhjoshi.codeexecutionengine.config.ComplexityProfileExecutionProperties;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComplexityProfileChildJvmIsolationTest {

    private ComplexityProfileJavaHarness harness;
    private ComplexityProfileExecutionProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new ComplexityProfileExecutionProperties();
        properties.getHarness().setUseChildJvm(true);
        properties.getHarness().setCooperativeInProcessTimeouts(false);
        properties.getHarness().setChildJvmGraceMs(3_000);
        properties.getLimits().setMaxTotalProfileMs(2_000);
        properties.getLimits().setPerInvocationTimeoutMs(500);
        ExecutionRuntimeProperties runtimeProperties = new ExecutionRuntimeProperties();
        runtimeProperties.getLanguages().put("java", defaultJavaRuntime());
        ManagedProcessRunner processRunner = new ManagedProcessRunner();
        ComplexityProfileChildJvmRunner childJvmRunner = new ComplexityProfileChildJvmRunner(objectMapper, properties);
        harness = new ComplexityProfileJavaHarness(
                processRunner, runtimeProperties, properties, objectMapper, childJvmRunner);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void nonTerminatingCandidateDoesNotBlockSubsequentProfiles() {
        HarnessRunResult hung = harness.execute(infiniteLoopJob());
        assertTrue(hung.compileStatus().equals("FAILED") || hung.cases().stream()
                .anyMatch(c -> "TIMEOUT".equals(c.outcome()) || "TIMEOUT".equals(c.errorCode())
                        || "PROFILE_CHILD_TIMEOUT".equals(hung.errorMessage())),
                "expected timeout outcome, got " + hung);

        HarnessRunResult healthy = harness.execute(fastJob());
        assertEquals("SUCCESS", healthy.compileStatus());
        assertEquals("SUCCESS", healthy.cases().getFirst().outcome());
    }

    private ComplexityProfileJobPayload infiniteLoopJob() {
        return baseJob("""
                public class Solution {
                  public int sum(int[] nums) {
                    while (true) { /* non-terminating */ }
                    return 0;
                  }
                }
                """, 0, 1);
    }

    private ComplexityProfileJobPayload fastJob() {
        return baseJob("""
                public class Solution {
                  public int sum(int[] nums) {
                    return 7;
                  }
                }
                """, 0, 2);
    }

    private ComplexityProfileJobPayload baseJob(String source, int warmups, int measured) {
        var expected = objectMapper.valueToTree(7);
        ArrayNodeHelper input = new ArrayNodeHelper(objectMapper);
        ProfileCaseRequest profileCase = new ProfileCaseRequest(
                "c1", "id1", "P", "v1", "hash", "gv1", "RANDOM", Map.of("n", 4), "seed",
                input.arrayInput(4), "ih", expected, warmups, measured);
        return ComplexityProfileJobPayload.builder()
                .executionId("exec-child-" + source.hashCode())
                .submissionId("sub")
                .questionId(1L)
                .language("JAVA")
                .sourceCode(source)
                .questionMetadata(new QuestionMetadataDto(
                        "", "sum", "int", List.of(new ParameterDto("nums", "int[]")), Map.of(), "ALGORITHM", null))
                .profileCode("P")
                .profileVersion("v1")
                .profileHash("hash")
                .harnessVersion("v1")
                .cases(List.of(profileCase))
                .build();
    }

    private static ExecutionRuntimeProperties.LanguageRuntime defaultJavaRuntime() {
        ExecutionRuntimeProperties.LanguageRuntime runtime = new ExecutionRuntimeProperties.LanguageRuntime();
        runtime.setCompile("javac");
        runtime.setRun("java");
        return runtime;
    }

    /** tiny helper to avoid pulling ArrayNode into every method */
    private static final class ArrayNodeHelper {
        private final ObjectMapper mapper;

        ArrayNodeHelper(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        com.fasterxml.jackson.databind.node.ArrayNode arrayInput(int n) {
            var root = mapper.createArrayNode();
            var nums = mapper.createArrayNode();
            for (int i = 0; i < n; i++) {
                nums.add(i);
            }
            root.add(nums);
            return root;
        }
    }
}

package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.junit.jupiter.api.Assertions.*;

class ComplexityProfileHarnessTest {

    private ComplexityProfileJavaHarness harness;
    private ComplexityProfileExecutionProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new ComplexityProfileExecutionProperties();
        properties.getLimits().setPerInvocationTimeoutMs(5_000);
        properties.getLimits().setMaxTotalProfileMs(60_000);
        properties.getHarness().setUseChildJvm(false);
        properties.getHarness().setCooperativeInProcessTimeouts(true);
        ExecutionRuntimeProperties runtimeProperties = new ExecutionRuntimeProperties();
        runtimeProperties.getLanguages().put("java", defaultJavaRuntime());
        ManagedProcessRunner processRunner = new ManagedProcessRunner();
        ComplexityProfileChildJvmRunner childJvmRunner =
                new ComplexityProfileChildJvmRunner(objectMapper, properties);
        harness = new ComplexityProfileJavaHarness(
                processRunner, runtimeProperties, properties, objectMapper, childJvmRunner);
    }

    @Test
    void compileOnceRunManyAcrossCases() {
        ComplexityProfileJobPayload job = job(
                linearSumSource(),
                List.of(caseRequest("c1", 8, 1, 2), caseRequest("c2", 16, 1, 2)));
        HarnessRunResult result = harness.execute(job);
        assertEquals("SUCCESS", result.compileStatus());
        assertEquals(1, harness.getCompileInvocationCount());
        assertEquals(2, result.cases().size());
        result.cases().forEach(c -> assertEquals("SUCCESS", c.outcome(), String.valueOf(c.errorCode())));
        assertTrue(result.cases().stream().allMatch(c -> c.outputValidated()));
    }

    @Test
    void warmupsExcludedFromMeasuredSamples() {
        ProfileCaseRequest profileCase = caseRequest("warm", 32, 3, 4);
        HarnessRunResult result = harness.execute(job(linearSumSource(), List.of(profileCase)));
        assertEquals(4, result.cases().getFirst().sampleCount());
        assertEquals(3, result.cases().getFirst().warmupCount());
    }

    @Test
    void acceptsFourSumEquivalentCollectionOrdering() {
        var expected = objectMapper.createArrayNode();
        expected.add(objectMapper.createArrayNode().add(1).add(2).add(5).add(7));
        expected.add(objectMapper.createArrayNode().add(2).add(2).add(6).add(6));
        ArrayNode input = objectMapper.createArrayNode();
        input.add(objectMapper.createArrayNode().add(1).add(2).add(5).add(7).add(2).add(6));
        input.add(16);
        ProfileCaseRequest profileCase = new ProfileCaseRequest(
                "4sum", "id-4sum", "P", "v1", "hash", "gv1", "FIXED", Map.of("n", 6), "seed",
                input, "input-hash", expected, 0, 1);
        HarnessRunResult result = harness.execute(job(fourSumPermutedOrderSource(), List.of(profileCase), fourSumMetadata()));
        assertEquals("SUCCESS", result.cases().getFirst().outcome(), String.valueOf(result.cases().getFirst().errorCode()));
        assertTrue(result.cases().getFirst().outputValidated());
    }

    @Test
    void rejectsMutableStaticMemoizationForProfiling() {
        ProfileCaseRequest profileCase = caseRequest("static", 8, 0, 3);
        HarnessRunResult result = harness.execute(job(staticMemoSource(), List.of(profileCase)));
        assertEquals("UNSUPPORTED_MUTABLE_STATIC_STATE", result.cases().getFirst().outcome());
        assertEquals(0, result.cases().getFirst().sampleCount());
    }

    @Test
    void rejectsIncorrectOutputForInferenceUse() {
        var wrongExpected = objectMapper.valueToTree(999);
        ProfileCaseRequest profileCase = new ProfileCaseRequest(
                "bad", "id-bad", "P", "v1", "hash", "gv1", "RANDOM", Map.of("n", 8), "seed",
                arrayInput(8), "input-hash", wrongExpected, 0, 2);
        HarnessRunResult result = harness.execute(job(linearSumSource(), List.of(profileCase)));
        assertEquals("OUTPUT_MISMATCH", result.cases().getFirst().outcome());
        assertFalse(result.cases().getFirst().outputValidated());
    }

    @Test
    void constantWorkloadMedianIsStableAcrossInputSizes() {
        long small = medianNs(job(constantTimeSource(), List.of(caseRequestConstant("s", 200, 0, 5))));
        long large = medianNs(job(constantTimeSource(), List.of(caseRequestConstant("l", 4000, 0, 5))));
        assertTrue(Math.abs(large - small) < small / 2 + 1);
    }

    @Test
    void linearWorkloadMedianIncreasesWithInputSize() {
        long small = medianNs(job(linearSumSource(), List.of(caseRequest("s", 200, 0, 5))));
        long large = medianNs(job(linearSumSource(), List.of(caseRequest("l", 4000, 0, 5))));
        assertTrue(large > small);
    }

    @Test
    void quadraticWorkloadGrowsFasterThanLinearBetweenSameBounds() {
        long linearSmall = medianNs(job(linearSumSource(), List.of(caseRequest("ls", 500, 0, 4))));
        long linearLarge = medianNs(job(linearSumSource(), List.of(caseRequest("ll", 1500, 0, 4))));
        long quadSmall = medianNs(job(quadraticSumSource(), List.of(quadraticCaseRequest("qs", 500, 0, 4))));
        long quadLarge = medianNs(job(quadraticSumSource(), List.of(quadraticCaseRequest("ql", 800, 0, 4))));
        long linearDelta = linearLarge - linearSmall;
        long quadDelta = quadLarge - quadSmall;
        assertTrue(quadDelta > linearDelta);
    }

    @Test
    void caseIdentityRoundTripInResults() {
        ProfileCaseRequest profileCase = caseRequest("case-identity-1", 16, 1, 2);
        HarnessRunResult result = harness.execute(job(linearSumSource(), List.of(profileCase)));
        assertEquals("case-identity-1", result.cases().getFirst().caseId());
        assertEquals("identity-" + profileCase.caseId(), result.cases().getFirst().caseIdentity());
    }

    private long medianNs(ComplexityProfileJobPayload job) {
        HarnessRunResult result = harness.execute(job);
        assertEquals("SUCCESS", result.cases().getFirst().outcome());
        return result.cases().getFirst().medianElapsedNs();
    }

    private ComplexityProfileJobPayload job(String source, List<ProfileCaseRequest> cases) {
        return job(source, cases, metadata());
    }

    private ComplexityProfileJobPayload job(String source, List<ProfileCaseRequest> cases, QuestionMetadataDto questionMetadata) {
        return ComplexityProfileJobPayload.builder()
                .executionId("exec-test")
                .submissionId("sub-test")
                .questionId(1L)
                .language("JAVA")
                .sourceCode(source)
                .questionMetadata(questionMetadata)
                .profileCode("TEST")
                .profileVersion("v1")
                .profileHash("hash")
                .harnessVersion("v1")
                .cases(cases)
                .build();
    }

    private ProfileCaseRequest caseRequestConstant(String caseId, int n, int warmups, int measured) {
        var expected = objectMapper.valueToTree(42);
        return new ProfileCaseRequest(
                caseId,
                "identity-" + caseId,
                "P",
                "v1",
                "hash",
                "gv1",
                "RANDOM",
                Map.of("n", n),
                "seed",
                arrayInput(n),
                "input-hash",
                expected,
                warmups,
                measured);
    }

    private ProfileCaseRequest quadraticCaseRequest(String caseId, int n, int warmups, int measured) {
        int expected = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                expected += i;
            }
        }
        return buildCaseRequest(caseId, n, warmups, measured, objectMapper.valueToTree(expected));
    }

    private ProfileCaseRequest caseRequest(String caseId, int n, int warmups, int measured) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        return buildCaseRequest(caseId, n, warmups, measured, objectMapper.valueToTree(sum));
    }

    private ProfileCaseRequest buildCaseRequest(
            String caseId, int n, int warmups, int measured, com.fasterxml.jackson.databind.JsonNode expected) {
        return new ProfileCaseRequest(
                caseId,
                "identity-" + caseId,
                "P",
                "v1",
                "hash",
                "gv1",
                "RANDOM",
                Map.of("n", n),
                "seed",
                arrayInput(n),
                "input-hash",
                expected,
                warmups,
                measured);
    }

    private ArrayNode arrayInput(int n) {
        ArrayNode root = objectMapper.createArrayNode();
        ArrayNode nums = objectMapper.createArrayNode();
        for (int i = 0; i < n; i++) {
            nums.add(i);
        }
        root.add(nums);
        return root;
    }

    private static QuestionMetadataDto metadata() {
        return new QuestionMetadataDto(
                "",
                "sum",
                "int",
                List.of(new ParameterDto("nums", "int[]")),
                Map.of(),
                "ALGORITHM",
                null);
    }

    private static QuestionMetadataDto fourSumMetadata() {
        return new QuestionMetadataDto(
                "",
                "fourSum",
                "int[][]",
                List.of(new ParameterDto("nums", "int[]"), new ParameterDto("target", "int")),
                Map.of(),
                "ALGORITHM",
                false);
    }

    private static String constantTimeSource() {
        return """
                public class Solution {
                  public int sum(int[] nums) {
                    return 42;
                  }
                }
                """;
    }

    private static String fourSumPermutedOrderSource() {
        return """
                public class Solution {
                  public int[][] fourSum(int[] nums, int target) {
                    return new int[][] { {2, 2, 6, 6}, {1, 2, 5, 7} };
                  }
                }
                """;
    }

    private static String staticMemoSource() {
        return """
                public class Solution {
                  static int hits = 0;
                  public int sum(int[] nums) {
                    hits++;
                    return hits;
                  }
                }
                """;
    }

    private static String linearSumSource() {
        return """
                public class Solution {
                  public int sum(int[] nums) {
                    int total = 0;
                    for (int value : nums) {
                      total += value;
                    }
                    return total;
                  }
                }
                """;
    }

    private static String quadraticSumSource() {
        return """
                public class Solution {
                  public int sum(int[] nums) {
                    int total = 0;
                    for (int i = 0; i < nums.length; i++) {
                      for (int j = 0; j < nums.length; j++) {
                        total += nums[i];
                      }
                    }
                    return total;
                  }
                }
                """;
    }

    private static ExecutionRuntimeProperties.LanguageRuntime defaultJavaRuntime() {
        ExecutionRuntimeProperties.LanguageRuntime runtime = new ExecutionRuntimeProperties.LanguageRuntime();
        runtime.setCompile("javac");
        runtime.setRun("java");
        return runtime;
    }
}

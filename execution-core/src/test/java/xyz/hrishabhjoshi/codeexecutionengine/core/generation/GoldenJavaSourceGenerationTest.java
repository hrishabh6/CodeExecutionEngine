package xyz.hrishabhjoshi.codeexecutionengine.core.generation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GoldenJavaSourceGenerationTest {

    @Test
    void generatesJacksonAwareMainForAlgorithmSubmission(@TempDir Path root) throws Exception {
        CodeSubmissionDTO submission = trapRainWaterSubmission();
        SourceWorkspaceGenerator.withDefaultRegistry().generateIntoWorkspace(submission, root);

        String main = Files.readString(root.resolve("q/trap/Main.java"));
        assertTrue(main.contains("com.fasterxml.jackson.databind.ObjectMapper"));
        assertTrue(main.contains("trapRainWater"));
    }

    private static CodeSubmissionDTO trapRainWaterSubmission() {
        CodeSubmissionDTO.QuestionMetadata metadata = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName("q.trap")
                .functionName("trapRainWater")
                .returnType("int")
                .questionType("ALGORITHM")
                .parameters(List.of(new ParamInfoDTO("heightMap", "int[][]")))
                .build();
        return CodeSubmissionDTO.builder()
                .submissionId("sub-golden")
                .executionId("exec-golden")
                .language("java")
                .userSolutionCode("class Solution { public int trapRainWater(int[][] heightMap) { return 0; } }")
                .questionMetadata(metadata)
                .testCases(List.of(Map.of(
                        "input",
                        Map.of("heightMap", List.of(
                                List.of(1, 4, 3),
                                List.of(3, 2, 1))))))
                .build();
    }
}

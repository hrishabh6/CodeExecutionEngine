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

class GoldenPythonSourceGenerationTest {

    @Test
    void generatesMainAndSolution(@TempDir Path root) throws Exception {
        CodeSubmissionDTO submission = CodeSubmissionDTO.builder()
                .submissionId("py-sub")
                .executionId("py-exec")
                .language("python")
                .userSolutionCode("def twoSum(nums, target):\n    return [0, 1]")
                .questionMetadata(CodeSubmissionDTO.QuestionMetadata.builder()
                        .fullyQualifiedPackageName("q.twosum")
                        .functionName("twoSum")
                        .returnType("List[int]")
                        .parameters(List.of(
                                new ParamInfoDTO("nums", "List[int]"),
                                new ParamInfoDTO("target", "int")))
                        .build())
                .testCases(List.of(Map.of("input", Map.of("nums", List.of(2, 7, 11, 15), "target", 9))))
                .build();

        SourceWorkspaceGenerator.withDefaultRegistry().generateIntoWorkspace(submission, root);
        Path packageDir = root.resolve("q/twosum");
        assertTrue(Files.exists(packageDir.resolve("main.py")));
        assertTrue(Files.exists(packageDir.resolve("solution.py")));
        assertTrue(Files.readString(packageDir.resolve("main.py")).contains("twoSum"));
    }
}

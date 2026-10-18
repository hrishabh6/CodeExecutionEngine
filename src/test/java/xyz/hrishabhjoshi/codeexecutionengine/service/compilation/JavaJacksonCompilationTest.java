package xyz.hrishabhjoshi.codeexecutionengine.service.compilation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.hrishabhjoshi.codeexecutionengine.config.ExecutionRuntimeProperties;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CompilationResult;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ParamInfoDTO;
import xyz.hrishabhjoshi.codeexecutionengine.service.execution.JavaExecutionService;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java.JavaFileGenerator;
import xyz.hrishabhjoshi.codeexecutionengine.service.utils.ManagedProcessRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaJacksonCompilationTest {

    private static final String TRAP_RAIN_WATER = """
            class Solution {
                public int trapRainWater(int[][] heightMap) {
                    if (heightMap == null || heightMap.length == 0 || heightMap[0].length == 0) {
                        return 0;
                    }
                    int m = heightMap.length;
                    int n = heightMap[0].length;
                    if (m < 3 || n < 3) {
                        return 0;
                    }
                    boolean[][] visited = new boolean[m][n];
                    java.util.PriorityQueue<int[]> pq = new java.util.PriorityQueue<>(
                            java.util.Comparator.comparingInt(a -> a[2]));
                    for (int i = 0; i < m; i++) {
                        for (int j = 0; j < n; j++) {
                            if (i == 0 || j == 0 || i == m - 1 || j == n - 1) {
                                pq.offer(new int[] {i, j, heightMap[i][j]});
                                visited[i][j] = true;
                            }
                        }
                    }
                    int result = 0;
                    int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                    while (!pq.isEmpty()) {
                        int[] cell = pq.poll();
                        for (int[] d : dirs) {
                            int x = cell[0] + d[0];
                            int y = cell[1] + d[1];
                            if (x < 0 || y < 0 || x >= m || y >= n || visited[x][y]) {
                                continue;
                            }
                            visited[x][y] = true;
                            result += Math.max(0, cell[2] - heightMap[x][y]);
                            pq.offer(new int[] {x, y, Math.max(cell[2], heightMap[x][y])});
                        }
                    }
                    return result;
                }
            }
            """;

    @Test
    void compilesAndRunsGeneratedTrapRainWaterMain(@TempDir Path root) throws Exception {
        CodeSubmissionDTO submission = submission();
        new JavaFileGenerator().generateFiles(submission, root);

        String main = Files.readString(root.resolve("q/trap/Main.java"));
        assertTrue(main.contains("com.fasterxml.jackson.databind.ObjectMapper"));
        assertTrue(main.contains("com.fasterxml.jackson.core.JsonProcessingException"));

        JavaCompilationService compiler = compiler();
        CompilationResult compiled = compiler.compile("sub-1", "q.trap", root, line -> { });
        assertTrue(compiled.isSuccess(), compiled.getOutput());

        JavaExecutionService runner = new JavaExecutionService(new ManagedProcessRunner(), runtime());
        ReflectionTestUtils.setField(runner, "executionTimeoutSeconds", 15L);
        ExecutionResult executed = runner.run("sub-1", root, "q.trap.Main", line -> { });

        assertEquals(0, executed.getExitCode(), executed.getRawOutput());
        assertFalse(executed.isTimedOut());
        assertTrue(executed.getRawOutput().contains("TEST_CASE_RESULT: 0,4,"), executed.getRawOutput());
    }

    @Test
    void compilerStderrIsReturnedForInvalidJava(@TempDir Path root) throws Exception {
        Path source = root.resolve("Broken.java");
        Files.writeString(source, "public class Broken { public static void main(String[] args) { int x = ; } }\n");

        CompilationResult compiled = compiler().compile("sub-2", "q.trap", root, line -> { });

        assertFalse(compiled.isSuccess());
        assertTrue(compiled.getOutput().contains("error:"), compiled.getOutput());
    }

    private static JavaCompilationService compiler() {
        return new JavaCompilationService(new ManagedProcessRunner(), runtime());
    }

    private static ExecutionRuntimeProperties runtime() {
        ExecutionRuntimeProperties properties = new ExecutionRuntimeProperties();
        ExecutionRuntimeProperties.LanguageRuntime java = new ExecutionRuntimeProperties.LanguageRuntime();
        java.setCompile(Path.of(System.getProperty("java.home"), "bin", "javac").toString());
        java.setRun(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        properties.getLanguages().put("java", java);
        return properties;
    }

    private static CodeSubmissionDTO submission() {
        CodeSubmissionDTO.QuestionMetadata metadata = CodeSubmissionDTO.QuestionMetadata.builder()
                .fullyQualifiedPackageName("q.trap")
                .functionName("trapRainWater")
                .returnType("int")
                .questionType("ALGORITHM")
                .parameters(List.of(new ParamInfoDTO("heightMap", "int[][]")))
                .build();
        return CodeSubmissionDTO.builder()
                .submissionId("sub-1")
                .executionId("exec-1")
                .language("java")
                .userSolutionCode(TRAP_RAIN_WATER)
                .questionMetadata(metadata)
                .testCases(List.of(Map.of(
                        "input",
                        Map.of("heightMap", List.of(
                                List.of(1, 4, 3, 1, 3, 2),
                                List.of(3, 2, 1, 3, 2, 4),
                                List.of(2, 3, 3, 2, 3, 1))))))
                .build();
    }
}

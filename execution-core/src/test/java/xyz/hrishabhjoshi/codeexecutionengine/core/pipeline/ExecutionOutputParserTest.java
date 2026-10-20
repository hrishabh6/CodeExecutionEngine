package xyz.hrishabhjoshi.codeexecutionengine.core.pipeline;

import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.dto.ExecutionResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExecutionOutputParserTest {

    @Test
    void parsesHarnessLineAndIgnoresSpoofedPlainStdout() {
        String output = """
                user printed this line
                TEST_CASE_RESULT: 0,4,12,
                another line
                """;
        List<ExecutionResult.TestCaseOutput> parsed =
                ExecutionOutputParser.parseHarnessOutput(output, 1024L, null);

        assertEquals(1, parsed.size());
        assertEquals(0, parsed.getFirst().getTestCaseIndex());
        assertEquals("4", parsed.getFirst().getActualOutput());
        assertEquals(12L, parsed.getFirst().getExecutionTimeMs());
        assertEquals(1024L, parsed.getFirst().getMemoryBytes());
    }

    @Test
    void adversarialSpoofWithoutPrefixIsNotParsedAsResult() {
        List<ExecutionResult.TestCaseOutput> parsed = ExecutionOutputParser.parseHarnessOutput(
                "TEST_CASE_RESULT 0,4,12", null, null);
        assertEquals(0, parsed.size());
    }
}

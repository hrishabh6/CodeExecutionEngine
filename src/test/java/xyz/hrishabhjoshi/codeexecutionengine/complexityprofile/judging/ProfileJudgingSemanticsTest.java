package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.judging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.ParameterDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileJudgingSemanticsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProfileJudgingSemantics semantics = new ProfileJudgingSemantics(objectMapper);

    @Test
    void unorderedNestedListAcceptsPermutedFourSumShape() {
        QuestionMetadataDto metadata = new QuestionMetadataDto(
                "", "fourSum", "int[][]", List.of(new ParameterDto("nums", "int[]"), new ParameterDto("target", "int")),
                Map.of(), "ALGORITHM", false);
        int[][] actual = {{2, 2, 6, 6}, {1, 2, 5, 7}};
        var expected = objectMapper.createArrayNode();
        expected.add(objectMapper.createArrayNode().add(1).add(2).add(5).add(7));
        expected.add(objectMapper.createArrayNode().add(2).add(2).add(6).add(6));
        assertTrue(semantics.matches(actual, expected, metadata));
    }

    @Test
    void unorderedNestedListRejectsWrongContents() {
        QuestionMetadataDto metadata = new QuestionMetadataDto(
                "", "fourSum", "int[][]", List.of(new ParameterDto("nums", "int[]"), new ParameterDto("target", "int")),
                Map.of(), "ALGORITHM", false);
        int[][] actual = {{9, 9, 9, 9}};
        var expected = objectMapper.createArrayNode();
        expected.add(objectMapper.createArrayNode().add(1).add(2).add(5).add(7));
        assertFalse(semantics.matches(actual, expected, metadata));
    }
}

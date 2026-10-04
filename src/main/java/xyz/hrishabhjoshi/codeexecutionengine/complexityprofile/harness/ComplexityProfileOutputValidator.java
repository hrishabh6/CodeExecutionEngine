package xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.harness;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.dto.ComplexityProfileExecutionDtos.QuestionMetadataDto;
import xyz.hrishabhjoshi.codeexecutionengine.complexityprofile.judging.ProfileJudgingSemantics;

@Component
public class ComplexityProfileOutputValidator {

    private final ObjectMapper objectMapper;
    private final ProfileJudgingSemantics judgingSemantics;

    public ComplexityProfileOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.judgingSemantics = new ProfileJudgingSemantics(objectMapper);
    }

    public boolean matchesExpected(Object actual, JsonNode expectedOutput, QuestionMetadataDto metadata) {
        return judgingSemantics.matches(actual, expectedOutput, metadata);
    }

    public boolean matchesExpected(JsonNode actualNode, JsonNode expectedOutput, QuestionMetadataDto metadata) {
        return judgingSemantics.matchesJson(actualNode, expectedOutput, metadata);
    }

    public String serializeForLimitCheck(Object actual) {
        JsonNode node = objectMapper.valueToTree(actual);
        return node.toString();
    }
}
